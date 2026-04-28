package com.shb.dashboard.model;

public final class WidgetDefinition {

    private String  widgetId;
    private String  targetDb;
    private String  querySql;
    private String  dynamicConfig;
    private boolean isActive = true;

    /** Default constructor required for Jackson deserialization from the admin API request body. */
    public WidgetDefinition() {}

    /**
     * Creates a fully-populated definition.  Used by the DAO read path when assembling
     * definitions from the three meta tables (WIDGET_MASTER, WIDGET_QUERY, WIDGET_CONFIG).
     */
    public WidgetDefinition(String widgetId, String targetDb, String querySql,
                            String dynamicConfig, boolean isActive) {
        this.widgetId      = widgetId;
        this.targetDb      = targetDb;
        this.querySql      = querySql;
        this.dynamicConfig = dynamicConfig;
        this.isActive      = isActive;
    }

    /** Returns the unique widget identifier as stored in WIDGET_MASTER. */
    public String  getWidgetId()      { return widgetId; }

    /** Returns the registered DataSource key the widget queries against (e.g., "TARGET_DB"). */
    public String  getTargetDb()      { return targetDb; }

    /** Returns the full SELECT statement reassembled from WIDGET_QUERY chunks. */
    public String  getQuerySql()      { return querySql; }

    /** Returns the uiSchema JSON string reassembled from WIDGET_CONFIG EAV rows. */
    public String  getDynamicConfig() { return dynamicConfig; }

    /** Returns {@code true} if the widget is currently visible on the dashboard. */
    public boolean isActive()         { return isActive; }

    /** Sets the widget identifier; called by the service when routing a PUT request. */
    public void setWidgetId(String widgetId)           { this.widgetId      = widgetId; }

    /** Sets the target DataSource key. */
    public void setTargetDb(String targetDb)           { this.targetDb      = targetDb; }

    /** Sets the raw SQL for the widget query. */
    public void setQuerySql(String querySql)           { this.querySql      = querySql; }

    /** Sets the uiSchema JSON string. */
    public void setDynamicConfig(String dynamicConfig) { this.dynamicConfig = dynamicConfig; }

    /** Overrides the active flag; used by the activate and deactivate service operations. */
    public void setActive(boolean active)              { this.isActive      = active; }
}
