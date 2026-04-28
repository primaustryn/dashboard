package com.shb.dashboard.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WidgetAuditDao {

    private final JdbcTemplate metaJdbcTemplate;

    /** Binds the meta-DB JdbcTemplate used to write audit records to WIDGET_AUDIT. */
    public WidgetAuditDao(JdbcTemplate metaJdbcTemplate) {
        this.metaJdbcTemplate = metaJdbcTemplate;
    }

    /**
     * Appends a timestamped audit entry to WIDGET_AUDIT recording which widget was
     * affected and what administrative action was performed (CREATE, UPDATE, ACTIVATE,
     * DEACTIVATE, DELETE, DEPLOY).  The {@code created_at} column is populated by a
     * DB default so the timestamp is authoritative regardless of application-server clock skew.
     */
    public void record(String widgetId, String action) {
        metaJdbcTemplate.update(
            "INSERT INTO WIDGET_AUDIT (widget_id, action) VALUES (?, ?)",
            widgetId, action);
    }
}
