-- ============================================================
-- META DB – stores widget definitions, not business data.
-- Adding a new widget = INSERT here; zero Java code changes.
-- In production: CREATE TABLE on Oracle/Tibero with CLOB columns.
-- ============================================================
CREATE TABLE IF NOT EXISTS WIDGET_MASTER (
    widget_id      VARCHAR(50)   NOT NULL,
    target_db      VARCHAR(100)  NOT NULL,
    query_sql      CLOB          NOT NULL,
    dynamic_config CLOB          NOT NULL,
    is_active      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_widget_master PRIMARY KEY (widget_id)
);
