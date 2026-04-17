package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shb.dashboard.dao.DynamicWidgetDao;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetMeta;
import com.shb.dashboard.model.WidgetResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Core orchestrator of the metadata-driven engine. Three responsibilities:
 *
 *   1. Read widget definition (SQL + UI schema) from the Meta DB.
 *   2. Route to the correct target DataSource via the registry.
 *   3. Execute the SQL safely and assemble the unified WidgetResponse.
 *
 * No widget-specific logic lives here - all behaviour is data-driven from
 * WIDGET_MASTER, so a new widget type requires only a database INSERT.
 */
@Service
public class WidgetEngineService {

    // Auto-configured by Spring Boot using the @Primary (meta) DataSource.
    private final JdbcTemplate metaJdbcTemplate;
    private final DynamicWidgetDao dynamicWidgetDao;
    private final Map<String, DataSource> dataSourceRegistry;
    private final ObjectMapper objectMapper;

    public WidgetEngineService(JdbcTemplate metaJdbcTemplate,
                               DynamicWidgetDao dynamicWidgetDao,
                               Map<String, DataSource> dataSourceRegistry,
                               ObjectMapper objectMapper) {
        this.metaJdbcTemplate   = metaJdbcTemplate;
        this.dynamicWidgetDao   = dynamicWidgetDao;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper       = objectMapper;
    }

    public WidgetResponse getWidgetData(String widgetId, Map<String, Object> params) {
        WidgetMeta meta     = fetchWidgetMeta(widgetId);
        JsonNode   uiSchema = parseUiSchema(widgetId, meta.dynamicConfig());
        DataSource targetDs = resolveDataSource(meta.targetDb());

        List<Map<String, Object>> data =
                dynamicWidgetDao.executeTargetQuery(targetDs, meta.querySql(), params);

        return new WidgetResponse(widgetId, uiSchema, data);
    }

    // ---- Private helpers ----------------------------------------------------

    private WidgetMeta fetchWidgetMeta(String widgetId) {
        // Plain string concat avoids text-block syntax (requires Java 15+)
        String sql = "SELECT widget_id, target_db, query_sql, dynamic_config"
                   + " FROM WIDGET_MASTER"
                   + " WHERE widget_id = ? AND is_active = TRUE";
        try {
            return metaJdbcTemplate.queryForObject(sql, (rs, row) -> new WidgetMeta(
                    rs.getString("widget_id"),
                    rs.getString("target_db"),
                    rs.getString("query_sql"),
                    rs.getString("dynamic_config")
            ), widgetId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WidgetNotFoundException(widgetId);
        }
    }

    private JsonNode parseUiSchema(String widgetId, String dynamicConfig) {
        try {
            return objectMapper.readTree(dynamicConfig);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Invalid JSON in WIDGET_MASTER.dynamic_config for widget: " + widgetId, ex);
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
