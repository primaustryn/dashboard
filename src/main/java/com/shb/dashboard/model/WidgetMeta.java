package com.shb.dashboard.model;

/**
 * Internal representation of a WIDGET_MASTER row.
 * Not exposed over the API - engine-internal transport object only.
 */
public final class WidgetMeta {

    private final String widgetId;
    private final String targetDb;
    private final String querySql;
    private final String dynamicConfig; // raw JSON string; parsed to JsonNode in service layer

    public WidgetMeta(String widgetId, String targetDb, String querySql, String dynamicConfig) {
        this.widgetId      = widgetId;
        this.targetDb      = targetDb;
        this.querySql      = querySql;
        this.dynamicConfig = dynamicConfig;
    }

    // Record-style accessors (no 'get' prefix) so service call sites need no changes
    public String widgetId()      { return widgetId; }
    public String targetDb()      { return targetDb; }
    public String querySql()      { return querySql; }
    public String dynamicConfig() { return dynamicConfig; }
}
