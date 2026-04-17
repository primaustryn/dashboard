package com.shb.dashboard.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * The single, unified API response for every widget.
 *
 * One generic response type - rather than a DTO per widget - is the core
 * contract that enables "Zero Code Change for New Widgets". The frontend
 * drives all rendering decisions from the uiSchema at runtime.
 *
 * widgetId  : the widget identifier echoed back to the caller
 * uiSchema  : parsed JSON from WIDGET_MASTER.dynamic_config (chart_type, axes, series)
 * data      : query result rows, each as a column-name to value map
 */
public final class WidgetResponse {

    private final String widgetId;
    private final JsonNode uiSchema;
    private final List<Map<String, Object>> data;

    public WidgetResponse(String widgetId, JsonNode uiSchema, List<Map<String, Object>> data) {
        this.widgetId = widgetId;
        this.uiSchema = uiSchema;
        this.data     = data;
    }

    // Standard getters - Jackson serialises these as camelCase JSON properties
    public String getWidgetId()                    { return widgetId; }
    public JsonNode getUiSchema()                  { return uiSchema; }
    public List<Map<String, Object>> getData()     { return data; }
}
