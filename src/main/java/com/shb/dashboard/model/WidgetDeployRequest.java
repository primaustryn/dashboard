package com.shb.dashboard.model;

import java.util.Map;

/**
 * Internal model produced by parsing a GitOps YAML deploy file.
 *
 * GitOps YAML contract:
 * <pre>
 * widgetId: WD_SALES_REGION          # uppercase, underscore-separated
 * targetDb: TARGET_DB                # must match a registered DataSource key
 * sql: |
 *   SELECT region, SUM(amount) AS total_amount
 *   FROM SALES_SUMMARY
 *   GROUP BY region
 * uiSchema:
 *   visualization: comparison        # semantic intent, not library-specific
 *   priority: high                   # optional: critical | high | medium | low
 *   title: "Regional Sales"
 *   xAxis: { field: region, label: Region }
 *   yAxis: { label: "Amount (USD)" }
 *   series:
 *     - { name: "Total Sales", valueField: total_amount }
 * </pre>
 *
 * This class is immutable; all fields are set via the constructor after
 * SnakeYAML parses the raw YAML string.
 */
public final class WidgetDeployRequest {

    private final String              widgetId;
    private final String              targetDb;
    private final String              sql;
    private final Map<String, Object> uiSchema;

    public WidgetDeployRequest(String widgetId,
                               String targetDb,
                               String sql,
                               Map<String, Object> uiSchema) {
        this.widgetId = widgetId;
        this.targetDb = targetDb;
        this.sql      = sql;
        this.uiSchema = uiSchema;
    }

    public String              getWidgetId() { return widgetId; }
    public String              getTargetDb() { return targetDb; }
    public String              getSql()      { return sql; }
    public Map<String, Object> getUiSchema() { return uiSchema; }
}
