package com.shb.dashboard.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shb.dashboard.model.WidgetDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Persistence layer for widget definitions stored across three VARCHAR-only tables:
 *
 *   WIDGET_MASTER  – one row per widget (widget_id, target_db, is_active)
 *   WIDGET_QUERY   – query SQL split into ≤4 KB chunks, reassembled by chunk_order
 *   WIDGET_CONFIG  – EAV config; each top-level JSON key stored as one row
 *
 * The external API contract (WidgetDefinition with querySql / dynamicConfig strings)
 * is unchanged — callers see no difference from the previous CLOB design.
 */
@Repository
public class WidgetDefinitionDao {

    private static final int CHUNK_SIZE = 4000;

    private final JdbcTemplate metaJdbcTemplate;
    private final ObjectMapper  objectMapper;

    /**
     * Injects the meta-DB JdbcTemplate and the shared ObjectMapper used for
     * JSON serialization and deserialization of the EAV config entries.
     */
    public WidgetDefinitionDao(JdbcTemplate metaJdbcTemplate, ObjectMapper objectMapper) {
        this.metaJdbcTemplate = metaJdbcTemplate;
        this.objectMapper     = objectMapper;
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * Inserts a new widget into WIDGET_MASTER and writes its SQL in chunks to
     * WIDGET_QUERY and its uiSchema keys as EAV rows to WIDGET_CONFIG.
     */
    public void insert(WidgetDefinition def) {
        metaJdbcTemplate.update(
            "INSERT INTO WIDGET_MASTER (widget_id, target_db, is_active) VALUES (?, ?, ?)",
            def.getWidgetId(), def.getTargetDb(), def.isActive()
        );
        insertQueryChunks(def.getWidgetId(), def.getQuerySql());
        insertConfigEntries(def.getWidgetId(), def.getDynamicConfig());
    }

    /**
     * Updates the {@code target_db} in WIDGET_MASTER, then atomically replaces all
     * WIDGET_QUERY chunks and WIDGET_CONFIG EAV rows for the widget.
     *
     * @return the number of WIDGET_MASTER rows updated (0 means the widget was not found)
     */
    public int update(WidgetDefinition def) {
        int updated = metaJdbcTemplate.update(
            "UPDATE WIDGET_MASTER SET target_db = ? WHERE widget_id = ?",
            def.getTargetDb(), def.getWidgetId()
        );
        if (updated > 0) {
            metaJdbcTemplate.update("DELETE FROM WIDGET_QUERY  WHERE widget_id = ?", def.getWidgetId());
            metaJdbcTemplate.update("DELETE FROM WIDGET_CONFIG WHERE widget_id = ?", def.getWidgetId());
            insertQueryChunks(def.getWidgetId(), def.getQuerySql());
            insertConfigEntries(def.getWidgetId(), def.getDynamicConfig());
        }
        return updated;
    }

    /**
     * Flips the {@code is_active} flag on WIDGET_MASTER for the given widget.
     *
     * @return 1 on success, 0 if no WIDGET_MASTER row was found for the widgetId
     */
    public int setActive(String widgetId, boolean active) {
        return metaJdbcTemplate.update(
            "UPDATE WIDGET_MASTER SET is_active = ? WHERE widget_id = ?", active, widgetId
        );
    }

    /**
     * Deletes the widget from all four meta tables in dependency order:
     * WIDGET_PAYLOAD → WIDGET_QUERY → WIDGET_CONFIG → WIDGET_MASTER.
     *
     * @return the number of WIDGET_MASTER rows deleted (0 = widget not found)
     */
    public int delete(String widgetId) {
        metaJdbcTemplate.update("DELETE FROM WIDGET_PAYLOAD WHERE widget_id = ?", widgetId);
        metaJdbcTemplate.update("DELETE FROM WIDGET_QUERY  WHERE widget_id = ?", widgetId);
        metaJdbcTemplate.update("DELETE FROM WIDGET_CONFIG WHERE widget_id = ?", widgetId);
        return metaJdbcTemplate.update("DELETE FROM WIDGET_MASTER WHERE widget_id = ?", widgetId);
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    /** Returns assembled {@link WidgetDefinition} objects for all widgets, active or inactive. */
    public List<WidgetDefinition> findAll() {
        return assembleDefinitions(false);
    }

    /** Returns assembled {@link WidgetDefinition} objects for widgets with {@code is_active = true} only. */
    public List<WidgetDefinition> findActive() {
        return assembleDefinitions(true);
    }

    // =========================================================================
    // Private helpers — write
    // =========================================================================

    /**
     * Splits {@code querySql} into ≤ {@value #CHUNK_SIZE}-character segments and inserts
     * them in order into WIDGET_QUERY.  Always writes at least one row (empty string)
     * so that the re-assembly query always returns a result for any registered widget.
     */
    private void insertQueryChunks(String widgetId, String querySql) {
        String sql = (querySql == null) ? "" : querySql;
        int order = 0;
        for (int i = 0; i < sql.length(); i += CHUNK_SIZE) {
            String chunk = sql.substring(i, Math.min(i + CHUNK_SIZE, sql.length()));
            metaJdbcTemplate.update(
                "INSERT INTO WIDGET_QUERY (widget_id, chunk_order, chunk_text) VALUES (?, ?, ?)",
                widgetId, order++, chunk
            );
        }
        if (order == 0) {
            // Keep at least one row so the widget always has a query entry.
            metaJdbcTemplate.update(
                "INSERT INTO WIDGET_QUERY (widget_id, chunk_order, chunk_text) VALUES (?, ?, ?)",
                widgetId, 0, ""
            );
        }
    }

    /**
     * Parses {@code dynamicConfig} as a JSON object and stores each top-level key as an
     * independent EAV row in WIDGET_CONFIG.  Non-JSON {@code dynamicConfig} values throw
     * {@link IllegalArgumentException}.
     */
    private void insertConfigEntries(String widgetId, String dynamicConfig) {
        if (dynamicConfig == null || dynamicConfig.isBlank()) return;
        JsonNode root;
        try {
            root = objectMapper.readTree(dynamicConfig);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                "dynamicConfig is not valid JSON for widget: " + widgetId, ex);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            metaJdbcTemplate.update(
                "INSERT INTO WIDGET_CONFIG (widget_id, config_key, config_val) VALUES (?, ?, ?)",
                widgetId, entry.getKey(), entry.getValue().toString()
            );
        }
    }

    // =========================================================================
    // Private helpers — read / reassembly
    // =========================================================================

    /**
     * Loads all widgets in three bulk queries (no N+1) then assembles in Java.
     * H2 returns column-name map keys in UPPER_CASE, hence the uppercase literals.
     */
    private List<WidgetDefinition> assembleDefinitions(boolean activeOnly) {
        String whereActive = activeOnly
            ? " WHERE is_active = TRUE"
            : "";
        String subWhere = activeOnly
            ? " WHERE widget_id IN (SELECT widget_id FROM WIDGET_MASTER WHERE is_active = TRUE)"
            : "";

        // 1. Master rows (preserve insertion order by widget_id)
        List<Map<String, Object>> masterRows = metaJdbcTemplate.queryForList(
            "SELECT widget_id, target_db, is_active FROM WIDGET_MASTER" + whereActive
            + " ORDER BY widget_id"
        );
        if (masterRows.isEmpty()) return Collections.emptyList();

        // 2. All query chunks for the relevant widgets
        Map<String, StringBuilder> sqlByWidget = new LinkedHashMap<>();
        for (Map<String, Object> row : metaJdbcTemplate.queryForList(
                "SELECT widget_id, chunk_text FROM WIDGET_QUERY" + subWhere
                + " ORDER BY widget_id, chunk_order")) {
            String wid = (String) row.get("WIDGET_ID");
            sqlByWidget.computeIfAbsent(wid, k -> new StringBuilder())
                       .append(row.get("CHUNK_TEXT"));
        }

        // 3. All config entries for the relevant widgets
        Map<String, LinkedHashMap<String, String>> configByWidget = new LinkedHashMap<>();
        for (Map<String, Object> row : metaJdbcTemplate.queryForList(
                "SELECT widget_id, config_key, config_val FROM WIDGET_CONFIG" + subWhere
                + " ORDER BY widget_id, config_key")) {
            String wid = (String) row.get("WIDGET_ID");
            String key = (String) row.get("CONFIG_KEY");
            String val = (String) row.get("CONFIG_VAL");
            configByWidget.computeIfAbsent(wid, k -> new LinkedHashMap<>()).put(key, val);
        }

        // 4. Assemble WidgetDefinition for each master row
        List<WidgetDefinition> result = new ArrayList<>();
        for (Map<String, Object> row : masterRows) {
            String wid      = (String)  row.get("WIDGET_ID");
            String targetDb = (String)  row.get("TARGET_DB");
            boolean active  = (Boolean) row.get("IS_ACTIVE");

            String querySql     = sqlByWidget.getOrDefault(wid, new StringBuilder()).toString();
            String dynamicConfig = rebuildJson(
                wid, configByWidget.getOrDefault(wid, new LinkedHashMap<>()));

            result.add(new WidgetDefinition(wid, targetDb, querySql, dynamicConfig, active));
        }
        return result;
    }

    /**
     * Reconstructs a JSON object string from EAV entries.
     * Each config_val is a JSON-encoded value that is parsed back to a JsonNode,
     * preserving the original types (string, number, boolean, array, object).
     * Falls back to storing the raw string value if parsing fails.
     */
    private String rebuildJson(String widgetId, Map<String, String> entries) {
        ObjectNode node = objectMapper.createObjectNode();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            try {
                node.set(e.getKey(), objectMapper.readTree(e.getValue()));
            } catch (JsonProcessingException ex) {
                node.put(e.getKey(), e.getValue()); // fallback: store as plain string
            }
        }
        return node.toString();
    }
}
