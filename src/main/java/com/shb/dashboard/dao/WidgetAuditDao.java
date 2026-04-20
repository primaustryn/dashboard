package com.shb.dashboard.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WidgetAuditDao {

    private final JdbcTemplate metaJdbcTemplate;

    public WidgetAuditDao(JdbcTemplate metaJdbcTemplate) {
        this.metaJdbcTemplate = metaJdbcTemplate;
    }

    public void record(String widgetId, String action) {
        metaJdbcTemplate.update(
            "INSERT INTO WIDGET_AUDIT (widget_id, action) VALUES (?, ?)",
            widgetId, action);
    }
}
