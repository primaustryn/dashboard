-- ============================================================
-- TARGET DB – business data source (dev: H2, prod: Oracle/Tibero).
-- All schema changes are managed here — never via runtime API.
-- ============================================================

-- ── Core sales / trading data ─────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS SALES_SUMMARY (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    region    VARCHAR(100)  NOT NULL,
    product   VARCHAR(100)  NOT NULL,
    amount    DECIMAL(18,2) NOT NULL,
    sale_date DATE          NOT NULL,
    CONSTRAINT pk_sales_summary PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS TRADE_SUMMARY (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    symbol     VARCHAR(20)   NOT NULL,
    desk       VARCHAR(50)   NOT NULL,
    notional   DECIMAL(20,2) NOT NULL,
    trade_date DATE          NOT NULL,
    CONSTRAINT pk_trade_summary PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS RISK_SUMMARY (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    portfolio   VARCHAR(100)  NOT NULL,
    var_amount  DECIMAL(20,2) NOT NULL,
    report_date DATE          NOT NULL,
    CONSTRAINT pk_risk_summary PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS FX_RATE (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    pair      VARCHAR(20)   NOT NULL,
    rate      DECIMAL(12,4) NOT NULL,
    rate_date DATE          NOT NULL,
    CONSTRAINT pk_fx_rate PRIMARY KEY (id)
);

-- ── Risk / portfolio analytics ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS RISK_MATRIX (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    desk      VARCHAR(50)   NOT NULL,
    risk_type VARCHAR(50)   NOT NULL,
    score     DECIMAL(5,2)  NOT NULL,
    CONSTRAINT pk_risk_matrix PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS PORTFOLIO_SCORES (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    portfolio         VARCHAR(50)   NOT NULL,
    market_score      DECIMAL(5,2)  NOT NULL,
    credit_score      DECIMAL(5,2)  NOT NULL,
    liquidity_score   DECIMAL(5,2)  NOT NULL,
    op_score          DECIMAL(5,2)  NOT NULL,
    compliance_score  DECIMAL(5,2)  NOT NULL,
    CONSTRAINT pk_portfolio_scores PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS VAR_UTILIZATION (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    portfolio   VARCHAR(50)   NOT NULL,
    utilization DECIMAL(5,2)  NOT NULL,
    CONSTRAINT pk_var_utilization PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ASSET_PERFORMANCE (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    asset      VARCHAR(60)   NOT NULL,
    risk_pct   DECIMAL(6,2)  NOT NULL,
    return_pct DECIMAL(6,2)  NOT NULL,
    volume     DECIMAL(20,2) NOT NULL,
    CONSTRAINT pk_asset_performance PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS PORTFOLIO_AUM (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    region      VARCHAR(50)   NOT NULL,
    asset_class VARCHAR(50)   NOT NULL,
    aum         DECIMAL(20,2) NOT NULL,
    CONSTRAINT pk_portfolio_aum PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS GLOBAL_EXPOSURE (
    id       BIGINT        NOT NULL AUTO_INCREMENT,
    country  VARCHAR(100)  NOT NULL,
    exposure DECIMAL(20,2) NOT NULL,
    CONSTRAINT pk_global_exposure PRIMARY KEY (id)
);

-- ── OHLC price data ───────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS FX_OHLC_DAILY (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    pair         VARCHAR(20)   NOT NULL,
    trade_date   DATE          NOT NULL,
    open_price   DECIMAL(12,4) NOT NULL,
    close_price  DECIMAL(12,4) NOT NULL,
    low_price    DECIMAL(12,4) NOT NULL,
    high_price   DECIMAL(12,4) NOT NULL,
    trade_volume DECIMAL(22,2) NOT NULL,
    CONSTRAINT pk_fx_ohlc_daily PRIMARY KEY (id)
);
