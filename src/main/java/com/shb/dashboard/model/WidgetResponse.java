package com.shb.dashboard.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class WidgetResponse {

    private final String widgetId;
    private final JsonNode uiSchema;
    private final List<Map<String, Object>> data;
    // true when the query returned more than DynamicWidgetDao.MAX_ROWS rows.
    private final boolean truncated;

    public WidgetResponse(String widgetId, JsonNode uiSchema,
                          List<Map<String, Object>> data, boolean truncated) {
        this.widgetId  = widgetId;
        this.uiSchema  = uiSchema;
        this.data      = data;
        this.truncated = truncated;
    }

    public String getWidgetId()                    { return widgetId; }
    public JsonNode getUiSchema()                  { return uiSchema; }
    public List<Map<String, Object>> getData()     { return data; }
    public boolean isTruncated()                   { return truncated; }
}
