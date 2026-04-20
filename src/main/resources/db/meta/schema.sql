-- ============================================================
-- META DB – Widget definitions + audit tables.
-- No CLOB / TEXT / BLOB columns; every column is VARCHAR(N) or BOOLEAN.
-- ============================================================

-- Core identity: one row per widget, scalar metadata only.
CREATE TABLE IF NOT EXISTS WIDGET_MASTER (
    widget_id   VARCHAR(50)   NOT NULL,
    target_db   VARCHAR(100)  NOT NULL,
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_widget_master PRIMARY KEY (widget_id)
);

-- Query SQL split into ordered 4 KB chunks.
CREATE TABLE IF NOT EXISTS WIDGET_QUERY (
    widget_id    VARCHAR(50)   NOT NULL,
    chunk_order  INT           NOT NULL,
    chunk_text   VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_widget_query PRIMARY KEY (widget_id, chunk_order),
    CONSTRAINT fk_wq_master    FOREIGN KEY (widget_id) REFERENCES WIDGET_MASTER(widget_id)
);

-- EAV config: each top-level JSON key stored as one row.
CREATE TABLE IF NOT EXISTS WIDGET_CONFIG (
    widget_id    VARCHAR(50)   NOT NULL,
    config_key   VARCHAR(100)  NOT NULL,
    config_val   VARCHAR(1000),
    CONSTRAINT pk_widget_config PRIMARY KEY (widget_id, config_key),
    CONSTRAINT fk_wc_master     FOREIGN KEY (widget_id) REFERENCES WIDGET_MASTER(widget_id)
);

-- Widget lifecycle audit: records every CREATE / UPDATE / DELETE / ACTIVATE / DEACTIVATE.
CREATE TABLE IF NOT EXISTS WIDGET_AUDIT (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    widget_id   VARCHAR(50)   NOT NULL,
    action      VARCHAR(20)   NOT NULL,
    changed_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_widget_audit PRIMARY KEY (id)
);

-- HTTP request audit: one row per /api/** call, immutable record for compliance.
CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    request_ts  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    http_method VARCHAR(10)   NOT NULL,
    request_uri VARCHAR(500)  NOT NULL,
    client_ip   VARCHAR(50),
    status_code INT,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
