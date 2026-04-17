package com.shb.dashboard.dao;

import com.shb.dashboard.model.WidgetDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class WidgetDefinitionDao {

    private final JdbcTemplate metaJdbcTemplate;

    public WidgetDefinitionDao(JdbcTemplate metaJdbcTemplate) {
        this.metaJdbcTemplate = metaJdbcTemplate;
    }

    private static final String SELECT_COLS =
        "SELECT widget_id, target_db, query_sql, dynamic_config, is_active FROM WIDGET_MASTER";

    private WidgetDefinition map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new WidgetDefinition(
            rs.getString("widget_id"),
            rs.getString("target_db"),
            rs.getString("query_sql"),
            rs.getString("dynamic_config"),
            rs.getBoolean("is_active")
        );
    }

    public void insert(WidgetDefinition def) {
        metaJdbcTemplate.update(
            "INSERT INTO WIDGET_MASTER (widget_id, target_db, query_sql, dynamic_config, is_active)"
            + " VALUES (?, ?, ?, ?, ?)",
            def.getWidgetId(), def.getTargetDb(), def.getQuerySql(),
            def.getDynamicConfig(), def.isActive()
        );
    }

    public int update(WidgetDefinition def) {
        return metaJdbcTemplate.update(
            "UPDATE WIDGET_MASTER SET target_db=?, query_sql=?, dynamic_config=?"
            + " WHERE widget_id=?",
            def.getTargetDb(), def.getQuerySql(), def.getDynamicConfig(), def.getWidgetId()
        );
    }

    public int setActive(String widgetId, boolean active) {
        return metaJdbcTemplate.update(
            "UPDATE WIDGET_MASTER SET is_active=? WHERE widget_id=?", active, widgetId
        );
    }

    public List<WidgetDefinition> findAll() {
        return metaJdbcTemplate.query(SELECT_COLS + " ORDER BY widget_id", this::map);
    }

    public List<WidgetDefinition> findActive() {
        return metaJdbcTemplate.query(
            SELECT_COLS + " WHERE is_active=TRUE ORDER BY widget_id", this::map
        );
    }

    public int delete(String widgetId) {
        return metaJdbcTemplate.update(
            "DELETE FROM WIDGET_MASTER WHERE widget_id=?", widgetId
        );
    }
}
