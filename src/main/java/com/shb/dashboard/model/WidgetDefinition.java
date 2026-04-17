package com.shb.dashboard.model;

public final class WidgetDefinition {

    private String  widgetId;
    private String  targetDb;
    private String  querySql;
    private String  dynamicConfig;
    private boolean isActive = true;

    public WidgetDefinition() {}

    public WidgetDefinition(String widgetId, String targetDb, String querySql,
                            String dynamicConfig, boolean isActive) {
        this.widgetId      = widgetId;
        this.targetDb      = targetDb;
        this.querySql      = querySql;
        this.dynamicConfig = dynamicConfig;
        this.isActive      = isActive;
    }

    public String  getWidgetId()      { return widgetId; }
    public String  getTargetDb()      { return targetDb; }
    public String  getQuerySql()      { return querySql; }
    public String  getDynamicConfig() { return dynamicConfig; }
    public boolean isActive()         { return isActive; }

    public void setWidgetId(String widgetId)           { this.widgetId      = widgetId; }
    public void setTargetDb(String targetDb)           { this.targetDb      = targetDb; }
    public void setQuerySql(String querySql)           { this.querySql      = querySql; }
    public void setDynamicConfig(String dynamicConfig) { this.dynamicConfig = dynamicConfig; }
    public void setActive(boolean active)              { this.isActive      = active; }
}
