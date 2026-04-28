package com.shb.dashboard.controller;

import com.shb.dashboard.model.WidgetResponse;
import com.shb.dashboard.service.WidgetEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Single generic endpoint for every widget type.
 *
 * The contract is intentionally opaque to individual widget semantics:
 * the caller provides a widgetId and any optional filter parameters;
 * the engine resolves everything else from metadata at runtime.
 *
 * Example calls:
 *   GET /api/v1/widgets/WD_SALES_01
 *   GET /api/v1/widgets/WD_SALES_01?region=North
 */
@RestController
@RequestMapping("/api/v1/widgets")
public class WidgetEngineController {

    private final WidgetEngineService widgetEngineService;

    /** Injects the engine service that resolves metadata, executes SQL, and assembles the response. */
    public WidgetEngineController(WidgetEngineService widgetEngineService) {
        this.widgetEngineService = widgetEngineService;
    }

    /**
     * Resolves and executes a widget, returning its uiSchema and query result data.
     *
     * @param widgetId  identifier matching {@code WIDGET_MASTER.widget_id}
     * @param params    optional filter parameters; bound to {@code :named} placeholders
     *                  in the widget's SQL via {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}
     * @return          200 OK with the combined uiSchema + data payload, or 404 if the widget
     *                  is not found / inactive
     */
    @GetMapping("/{widgetId}")
    public ResponseEntity<WidgetResponse> getWidget(
            @PathVariable String widgetId,
            @RequestParam Map<String, Object> params) {

        WidgetResponse response = widgetEngineService.getWidgetData(widgetId, params);
        return ResponseEntity.ok(response);
    }
}
