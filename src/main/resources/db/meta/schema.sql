-- ============================================================
-- META DB – Widget definitions in normalized VARCHAR-only tables.
-- No CLOB / TEXT / BLOB columns; every column is VARCHAR(N) or BOOLEAN.
-- Adding a new widget = INSERT rows; zero Java code changes.
-- ============================================================

-- Core identity: one row per widget, scalar metadata only.
CREATE TABLE IF NOT EXISTS WIDGET_MASTER (
    widget_id   VARCHAR(50)   NOT NULL,
    target_db   VARCHAR(100)  NOT NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_widget_master PRIMARY KEY (widget_id)
);

-- Query SQL split into ordered 4 KB chunks.
-- Reassemble by ORDER BY chunk_order and concatenate chunk_text.
CREATE TABLE IF NOT EXISTS WIDGET_QUERY (
    widget_id    VARCHAR(50)   NOT NULL,
    chunk_order  INT           NOT NULL,
    chunk_text   VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_widget_query PRIMARY KEY (widget_id, chunk_order),
    CONSTRAINT fk_wq_master    FOREIGN KEY (widget_id) REFERENCES WIDGET_MASTER(widget_id)
);

-- EAV config: each top-level JSON key stored as one row.
-- config_val holds the JSON-encoded representation of the value:
--   string  -> "bar"          (quoted)
--   number  -> 42
--   boolean -> true
--   array   -> [{"name":"X"}]
--   object  -> {"field":"y"}
CREATE TABLE IF NOT EXISTS WIDGET_CONFIG (
    widget_id    VARCHAR(50)   NOT NULL,
    config_key   VARCHAR(100)  NOT NULL,
    config_val   VARCHAR(1000),
    CONSTRAINT pk_widget_config PRIMARY KEY (widget_id, config_key),
    CONSTRAINT fk_wc_master     FOREIGN KEY (widget_id) REFERENCES WIDGET_MASTER(widget_id)
);
