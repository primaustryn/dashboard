package com.shb.dashboard.exception;

public class WidgetNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a message that includes the missing widgetId so that
     * callers and log consumers can immediately identify which widget was not found in
     * WIDGET_MASTER without inspecting the stack trace.
     */
    public WidgetNotFoundException(String widgetId) {
        super("Widget not found in WIDGET_MASTER: " + widgetId);
    }
}
