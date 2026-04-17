package com.shb.dashboard.exception;

public class WidgetNotFoundException extends RuntimeException {

    public WidgetNotFoundException(String widgetId) {
        super("Widget not found in WIDGET_MASTER: " + widgetId);
    }
}
