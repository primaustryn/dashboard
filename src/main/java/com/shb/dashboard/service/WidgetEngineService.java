package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shb.dashboard.dao.DynamicWidgetDao;
import com.shb.dashboard.dao.DynamicWidgetDao.QueryResult;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetMeta;
import com.shb.dashboard.model.WidgetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Service
public class WidgetEngineService {

    private static final Logger log = LoggerFactory.getLogger(WidgetEngineService.class);

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

        QueryResult result = dynamicWidgetDao.executeTargetQuery(targetDs, meta.querySql(), params);

        if (result.truncated()) {
            log.warn("Widget [{}] result truncated to {} rows", widgetId, DynamicWidgetDao.MAX_ROWS);
        }
        return new WidgetResponse(widgetId, uiSchema, result.rows(), result.truncated());
    }

    // ---- Private helpers ----------------------------------------------------

    private WidgetMeta fetchWidgetMeta(String widgetId) {
        String targetDb;
        try {
            targetDb = metaJdbcTemplate.queryForObject(
                "SELECT target_db FROM WIDGET_MASTER WHERE widget_id = ? AND is_active = TRUE",
                String.class, widgetId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WidgetNotFoundException(widgetId);
        }

        List<Map<String, Object>> chunks = metaJdbcTemplate.queryForList(
            "SELECT chunk_text FROM WIDGET_QUERY WHERE widget_id = ? ORDER BY chunk_order",
            widgetId);
        StringBuilder sqlBuilder = new StringBuilder();
        for (Map<String, Object> row : chunks) {
            sqlBuilder.append(row.get("CHUNK_TEXT"));
        }
        String querySql = sqlBuilder.toString();

        List<Map<String, Object>> configRows = metaJdbcTemplate.queryForList(
            "SELECT config_key, config_val FROM WIDGET_CONFIG WHERE widget_id = ? ORDER BY config_key",
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
        String dynamicConfig = configNode.toString();

        return new WidgetMeta(widgetId, targetDb, querySql, dynamicConfig);
    }

    private JsonNode parseUiSchema(String widgetId, String dynamicConfig) {
        try {
            return objectMapper.readTree(dynamicConfig);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Reconstructed dynamicConfig is not valid JSON for widget: " + widgetId, ex);
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
