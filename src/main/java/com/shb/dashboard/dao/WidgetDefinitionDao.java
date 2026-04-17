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

    public WidgetDefinitionDao(JdbcTemplate metaJdbcTemplate, ObjectMapper objectMapper) {
        this.metaJdbcTemplate = metaJdbcTemplate;
        this.objectMapper     = objectMapper;
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    public void insert(WidgetDefinition def) {
        metaJdbcTemplate.update(
            "INSERT INTO WIDGET_MASTER (widget_id, target_db, is_active) VALUES (?, ?, ?)",
            def.getWidgetId(), def.getTargetDb(), def.isActive()
        );
        insertQueryChunks(def.getWidgetId(), def.getQuerySql());
        insertConfigEntries(def.getWidgetId(), def.getDynamicConfig());
    }

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

    public int setActive(String widgetId, boolean active) {
        return metaJdbcTemplate.update(
            "UPDATE WIDGET_MASTER SET is_active = ? WHERE widget_id = ?", active, widgetId
        );
    }

    public int delete(String widgetId) {
        metaJdbcTemplate.update("DELETE FROM WIDGET_QUERY  WHERE widget_id = ?", widgetId);
        metaJdbcTemplate.update("DELETE FROM WIDGET_CONFIG WHERE widget_id = ?", widgetId);
        return metaJdbcTemplate.update("DELETE FROM WIDGET_MASTER WHERE widget_id = ?", widgetId);
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    public List<WidgetDefinition> findAll() {
        return assembleDefinitions(false);
    }

    public List<WidgetDefinition> findActive() {
        return assembleDefinitions(true);
    }

    // =========================================================================
    // Private helpers — write
    // =========================================================================

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
