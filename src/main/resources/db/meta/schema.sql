-- ============================================================
-- META DB – Widget definitions + audit tables.
-- No CLOB / TEXT / BLOB columns; every column is VARCHAR(N) or BOOLEAN.
--
-- Storage evolution:
--   v1 (legacy)  – WIDGET_QUERY (raw text chunks) + WIDGET_CONFIG (EAV rows)
--   v2 (current) – WIDGET_PAYLOAD (Base64-chunked binary-safe storage)
--
-- Base64 rationale: chunking raw UTF-8 at a 4 000-byte boundary can split a
-- multi-byte character (Korean, CJK) mid-codepoint, producing corrupt data.
-- Base64-encoding first guarantees every stored chunk contains only ASCII
-- characters, making the 4 000 VARCHAR limit completely safe regardless of
-- the original content's charset.
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

-- ── GitOps payload store (v2) ─────────────────────────────────────────────────
-- Replaces WIDGET_QUERY + WIDGET_CONFIG.  Each logical payload (SQL or UI_SCHEMA)
-- is Base64-encoded and split into ≤4 000-character chunks stored as separate rows.
-- Reassembly: ORDER BY chunk_order, then Base64-decode the concatenated string.
CREATE TABLE IF NOT EXISTS WIDGET_PAYLOAD (
    widget_id    VARCHAR(50)   NOT NULL,
    payload_type VARCHAR(20)   NOT NULL, -- 'SQL' | 'UI_SCHEMA'
    chunk_order  INT           NOT NULL,
    base64_data  VARCHAR(4000) NOT NULL,
    CONSTRAINT pk_widget_payload  PRIMARY KEY (widget_id, payload_type, chunk_order),
    CONSTRAINT fk_wp_master       FOREIGN KEY (widget_id) REFERENCES WIDGET_MASTER(widget_id),
    CONSTRAINT ck_wp_payload_type CHECK (payload_type IN ('SQL', 'UI_SCHEMA'))
);

-- ── Legacy tables (v1) — retained for backward compatibility ──────────────────
-- Widgets registered via the old POST /api/v1/admin/widgets endpoint still use
-- these tables.  The engine falls back to them when WIDGET_PAYLOAD is empty.
-- Migrate widgets to the GitOps path and remove these tables in a future release.

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
