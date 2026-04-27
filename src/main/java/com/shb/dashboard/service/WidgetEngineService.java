package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shb.dashboard.dao.DynamicWidgetDao;
import com.shb.dashboard.dao.DynamicWidgetDao.QueryResult;
import com.shb.dashboard.dao.WidgetPayloadDao;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetMeta;
import com.shb.dashboard.model.WidgetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Widget query engine — resolves a widgetId to executable SQL + uiSchema and
 * returns the rendered data payload to the frontend.
 *
 * Storage strategy (dual-path for zero-downtime migration):
 *
 *   Primary path (GitOps / WIDGET_PAYLOAD):
 *     Widgets deployed via POST /api/v1/admin/widgets/deploy store their SQL
 *     and uiSchema as Base64-chunked rows in WIDGET_PAYLOAD.  This engine
 *     reassembles the chunks and Base64-decodes them before execution.
 *
 *   Fallback path (legacy EAV / WIDGET_QUERY + WIDGET_CONFIG):
 *     Widgets registered via the old POST /api/v1/admin/widgets endpoint still
 *     live in the EAV tables.  The engine falls back to them automatically so
 *     existing widgets continue to work without re-registration.
 *     Remove this path once all widgets are migrated to the GitOps flow.
 *
 * Decode flow (primary path):
 *   WIDGET_PAYLOAD rows  →  concat chunk strings  →  single Base64 string
 *   →  Base64.decode()   →  original UTF-8 SQL / JSON  →  execute / parse
 */
@Service
public class WidgetEngineService {

    private static final Logger log = LoggerFactory.getLogger(WidgetEngineService.class);

    private final JdbcTemplate            metaJdbcTemplate;
    private final WidgetPayloadDao         widgetPayloadDao;
    private final DynamicWidgetDao         dynamicWidgetDao;
    private final Map<String, DataSource>  dataSourceRegistry;
    private final ObjectMapper             objectMapper;

    public WidgetEngineService(JdbcTemplate metaJdbcTemplate,
                               WidgetPayloadDao widgetPayloadDao,
                               DynamicWidgetDao dynamicWidgetDao,
                               Map<String, DataSource> dataSourceRegistry,
                               ObjectMapper objectMapper) {
        this.metaJdbcTemplate   = metaJdbcTemplate;
        this.widgetPayloadDao   = widgetPayloadDao;
        this.dynamicWidgetDao   = dynamicWidgetDao;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper       = objectMapper;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public WidgetResponse getWidgetData(String widgetId, Map<String, Object> params) {
        WidgetMeta meta     = fetchWidgetMeta(widgetId);
        JsonNode   uiSchema = parseUiSchema(widgetId, meta.dynamicConfig());
        DataSource targetDs = resolveDataSource(meta.targetDb());

        QueryResult result = dynamicWidgetDao.executeTargetQuery(targetDs, meta.querySql(), params);

        if (result.truncated()) {
            log.warn("Widget [{}] result truncated to {} rows", widgetId, DynamicWidgetDao.MAX_ROWS);
        }
        return new WidgetResponse(widgetId, uiSchema, result.rows(), result.truncated());
    }

    // =========================================================================
    // Meta resolution — dual-path
    // =========================================================================

    private WidgetMeta fetchWidgetMeta(String widgetId) {
        // 1. Master row is always required (both paths share WIDGET_MASTER)
        String targetDb = loadTargetDb(widgetId);

        // 2. Primary path: GitOps widgets stored in WIDGET_PAYLOAD (Base64-chunked)
        String sqlBase64 = widgetPayloadDao.loadAssembled(widgetId, "SQL");
        if (sqlBase64 != null && !sqlBase64.isBlank()) {
            return loadFromPayloadTable(widgetId, targetDb, sqlBase64);
        }

        // 3. Fallback: legacy EAV tables for pre-GitOps widgets
        log.debug("Widget [{}] not in WIDGET_PAYLOAD — using legacy EAV tables", widgetId);
        return loadFromLegacyTables(widgetId, targetDb);
    }

    // ── Primary path ─────────────────────────────────────────────────────────

    /**
     * Reassembles and decodes the SQL and uiSchema payloads from WIDGET_PAYLOAD.
     *
     * Decode steps:
     *   1. Chunks were written in order by chunk_order — loadAssembled() returns
     *      them concatenated into a single Base64 string.
     *   2. Base64.decode() reconstructs the original UTF-8 bytes.
     *   3. new String(..., UTF_8) converts bytes back to the text — Korean /
     *      CJK characters that were safely encoded are fully restored here.
     */
    private WidgetMeta loadFromPayloadTable(String widgetId, String targetDb, String sqlBase64) {
        String querySql = decodeBase64(sqlBase64);

        String uiSchemaBase64 = widgetPayloadDao.loadAssembled(widgetId, "UI_SCHEMA");
        if (uiSchemaBase64 == null || uiSchemaBase64.isBlank()) {
            throw new IllegalStateException(
                "Widget [" + widgetId + "] has a SQL payload but no UI_SCHEMA payload. "
                + "Re-deploy the widget via POST /api/v1/admin/widgets/deploy.");
        }
        String dynamicConfig = decodeBase64(uiSchemaBase64);

        log.debug("Widget [{}] loaded from WIDGET_PAYLOAD (GitOps path)", widgetId);
        return new WidgetMeta(widgetId, targetDb, querySql, dynamicConfig);
    }

    // ── Legacy fallback path ─────────────────────────────────────────────────

    /**
     * Reads raw SQL chunks from WIDGET_QUERY and EAV config rows from
     * WIDGET_CONFIG, then reconstructs a WidgetMeta identical to what the
     * primary path would produce.
     *
     * This path is retained during the GitOps migration so existing widgets
     * registered via the old admin API continue to function without downtime.
     *
     * @deprecated Migrate widgets to the GitOps deploy endpoint and remove
     *             this method along with WIDGET_QUERY / WIDGET_CONFIG tables.
     */
    @Deprecated
    private WidgetMeta loadFromLegacyTables(String widgetId, String targetDb) {
        // Raw SQL chunks (no Base64 — old storage did not encode)
        List<String> sqlChunks = metaJdbcTemplate.queryForList(
            "SELECT chunk_text FROM WIDGET_QUERY WHERE widget_id = ? ORDER BY chunk_order",
            String.class, widgetId);
        String querySql = String.join("", sqlChunks);

        // EAV config rows → rebuild JSON object
        List<Map<String, Object>> configRows = metaJdbcTemplate.queryForList(
            "SELECT config_key, config_val FROM WIDGET_CONFIG"
            + " WHERE widget_id = ? ORDER BY config_key",
            widgetId);
        ObjectNode configNode = objectMapper.createObjectNode();
        for (Map<String, Object> row : configRows) {
            String key = (String) row.get("CONFIG_KEY");
            String val = (String) row.get("CONFIG_VAL");
            try {
                configNode.set(key, objectMapper.readTree(val));
            } catch (JsonProcessingException ex) {
                configNode.put(key, val);
            }
        }
        return new WidgetMeta(widgetId, targetDb, querySql, configNode.toString());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String loadTargetDb(String widgetId) {
        try {
            return metaJdbcTemplate.queryForObject(
                "SELECT target_db FROM WIDGET_MASTER WHERE widget_id = ? AND is_active = TRUE",
                String.class, widgetId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WidgetNotFoundException(widgetId);
        }
    }

    /**
     * Base64-decodes a string back to its original UTF-8 text.
     * Mirrors the encode step in WidgetDeployService.encodeBase64().
     */
    private static String decodeBase64(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private JsonNode parseUiSchema(String widgetId, String dynamicConfig) {
        try {
            return objectMapper.readTree(dynamicConfig);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                "Reconstructed uiSchema is not valid JSON for widget: " + widgetId, ex);
        }
    }

    private DataSource resolveDataSource(String targetDb) {
        DataSource ds = dataSourceRegistry.get(targetDb);
        if (ds == null) {
            throw new IllegalArgumentException(
                "No DataSource registered for target_db key: '" + targetDb + "'. "
                + "Add an entry to DataSourceConfig#dataSourceRegistry.");
        }
        return ds;
    }
}
