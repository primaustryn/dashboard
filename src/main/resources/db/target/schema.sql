-- ============================================================
-- TARGET DB – the actual business data source.
-- This schema simulates a banking trade/sales data warehouse.
-- In production this would be a read-only Oracle/Tibero schema.
-- ============================================================
CREATE TABLE IF NOT EXISTS SALES_SUMMARY (
    id        BIGINT        NOT NULL AUTO_INCREMENT,
    region    VARCHAR(100)  NOT NULL,
    product   VARCHAR(100)  NOT NULL,
    amount    DECIMAL(18,2) NOT NULL,
    sale_date DATE          NOT NULL,
    CONSTRAINT pk_sales_summary PRIMARY KEY (id)
);
