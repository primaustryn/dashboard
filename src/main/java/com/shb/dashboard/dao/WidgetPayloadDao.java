package com.shb.dashboard.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence layer for WIDGET_PAYLOAD — the Base64-chunked storage table that
 * replaces the legacy WIDGET_QUERY (raw SQL chunks) and WIDGET_CONFIG (EAV rows).
 *
 * Why Base64 chunking at 4 000 chars?
 *   - Air-gapped financial DBs (Oracle, Tibero) forbid CLOB/TEXT columns.
 *   - Chunking raw UTF-8 at a byte boundary can split a multi-byte Korean or
 *     CJK codepoint across two rows, producing corrupt data on reassembly.
 *   - Base64-encoding first converts arbitrary bytes to 7-bit ASCII, making
 *     every character exactly one byte — the 4 000-char VARCHAR limit is then
 *     perfectly safe regardless of the original content's charset.
 *
 * Operations are intentionally simple (delete-then-insert) to guarantee
 * idempotency: deploying the same widget twice produces identical DB state.
 */
@Repository
public class WidgetPayloadDao {

    /** Must stay ≤ 4 000 to fit within a single VARCHAR(4000) column. */
    static final int CHUNK_SIZE = 4_000;

    private final JdbcTemplate metaJdbc;

    public WidgetPayloadDao(JdbcTemplate metaJdbc) {
        this.metaJdbc = metaJdbc;
    }

    // =========================================================================
    // Write
    // =========================================================================

    /**
     * Atomically replaces all chunks for the given (widgetId, payloadType) pair.
     *
     * @param widgetId    the widget whose payload to replace
     * @param payloadType "SQL" or "UI_SCHEMA"
     * @param base64      the fully Base64-encoded payload string (all charsets safe)
     * @return            the number of chunks (rows) written
     */
    public int replace(String widgetId, String payloadType, String base64) {
        metaJdbc.update(
            "DELETE FROM WIDGET_PAYLOAD WHERE widget_id = ? AND payload_type = ?",
            widgetId, payloadType);

        int chunkIndex = 0;
        for (int offset = 0; offset < base64.length(); offset += CHUNK_SIZE) {
            String chunk = base64.substring(offset, Math.min(offset + CHUNK_SIZE, base64.length()));
            metaJdbc.update(
                "INSERT INTO WIDGET_PAYLOAD (widget_id, payload_type, chunk_order, base64_data)"
                + " VALUES (?, ?, ?, ?)",
                widgetId, payloadType, chunkIndex++, chunk);
        }
        return chunkIndex;
    }

    /**
     * Removes all payload rows for a widget (called when a widget is deleted).
     */
    public void deleteAll(String widgetId) {
        metaJdbc.update("DELETE FROM WIDGET_PAYLOAD WHERE widget_id = ?", widgetId);
    }

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Reassembles the stored Base64 string by concatenating all chunks in order.
     * The caller is responsible for Base64-decoding the result.
     *
     * @return the reassembled Base64 string, or {@code null} if no chunks exist
     *         (widget was registered via the legacy admin API, not the deploy endpoint)
     */
    public String loadAssembled(String widgetId, String payloadType) {
        List<String> chunks = metaJdbc.queryForList(
            "SELECT base64_data FROM WIDGET_PAYLOAD"
            + " WHERE widget_id = ? AND payload_type = ?"
            + " ORDER BY chunk_order",
            String.class, widgetId, payloadType);

        if (chunks.isEmpty()) return null;

        // String.join("") avoids StringBuilder allocation for the common 1-chunk case.
        return String.join("", chunks);
    }
}
