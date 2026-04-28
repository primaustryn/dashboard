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

    /**
     * Constructs the full widget response from its resolved components.
     * All fields are set once at construction time; the object is effectively immutable
     * to prevent unintentional mutation after the engine has assembled the result.
     */
    public WidgetResponse(String widgetId, JsonNode uiSchema,
                          List<Map<String, Object>> data, boolean truncated) {
        this.widgetId  = widgetId;
        this.uiSchema  = uiSchema;
        this.data      = data;
        this.truncated = truncated;
    }

    /** Returns the widget identifier echoed from the request, used by the frontend's state management. */
    public String getWidgetId()                    { return widgetId; }

    /** Returns the parsed uiSchema as a {@link JsonNode} — directly serializable to the frontend. */
    public JsonNode getUiSchema()                  { return uiSchema; }

    /** Returns the query result rows to be rendered by the chart component. */
    public List<Map<String, Object>> getData()     { return data; }

    /**
     * Returns {@code true} if the result was capped at {@code DynamicWidgetDao.MAX_ROWS},
     * indicating that the query produced more rows than the engine allows.
     * The frontend should surface a warning when this flag is set.
     */
    public boolean isTruncated()                   { return truncated; }
}
