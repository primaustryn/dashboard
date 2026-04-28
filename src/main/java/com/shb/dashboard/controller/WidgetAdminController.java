package com.shb.dashboard.controller;

import com.shb.dashboard.model.WidgetDefinition;
import com.shb.dashboard.service.WidgetDefinitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Manages WIDGET_MASTER rows at runtime — no Java code change needed per widget.
 *
 * POST   /api/v1/admin/widgets              register a new widget
 * GET    /api/v1/admin/widgets?activeOnly=  list widgets (default: all)
 * PUT    /api/v1/admin/widgets/{id}         update SQL / config
 * PATCH  /api/v1/admin/widgets/{id}/activate
 * PATCH  /api/v1/admin/widgets/{id}/deactivate
 * DELETE /api/v1/admin/widgets/{id}         permanently remove
 */
@RestController
@RequestMapping("/api/v1/admin/widgets")
public class WidgetAdminController {

    private final WidgetDefinitionService widgetDefinitionService;

    /** Injects the service that manages WIDGET_MASTER registrations and audit events. */
    public WidgetAdminController(WidgetDefinitionService widgetDefinitionService) {
        this.widgetDefinitionService = widgetDefinitionService;
    }

    /**
     * Registers a new widget by writing to WIDGET_MASTER, WIDGET_QUERY, and WIDGET_CONFIG.
     * Returns 201 Created with a {@code Location} header pointing to the widget data endpoint.
     */
    @PostMapping
    public ResponseEntity<Void> register(@RequestBody WidgetDefinition def) {
        widgetDefinitionService.register(def);
        return ResponseEntity.created(URI.create("/api/v1/widgets/" + def.getWidgetId())).build();
    }

    /**
     * Lists widget definitions.  When {@code activeOnly=true}, returns only widgets with
     * {@code is_active = true}; otherwise returns all widgets regardless of active state.
     */
    @GetMapping
    public ResponseEntity<List<WidgetDefinition>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<WidgetDefinition> result = activeOnly
                ? widgetDefinitionService.listActive()
                : widgetDefinitionService.listAll();
        return ResponseEntity.ok(result);
    }

    /**
     * Replaces the SQL and uiSchema config of an existing widget.
     * The {@code widgetId} from the path is authoritative; any widgetId in the body is overridden.
     * Returns 204 No Content on success, 404 if the widget does not exist.
     */
    @PutMapping("/{widgetId}")
    public ResponseEntity<Void> update(
            @PathVariable String widgetId,
            @RequestBody WidgetDefinition def) {
        widgetDefinitionService.update(widgetId, def);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets {@code is_active = true} on the widget, making it visible on the dashboard.
     * Returns 204 No Content, or 404 if the widget is not found.
     */
    @PatchMapping("/{widgetId}/activate")
    public ResponseEntity<Void> activate(@PathVariable String widgetId) {
        widgetDefinitionService.activate(widgetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets {@code is_active = false} on the widget, hiding it from the dashboard without
     * deleting any data.  Returns 204 No Content, or 404 if the widget is not found.
     */
    @PatchMapping("/{widgetId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String widgetId) {
        widgetDefinitionService.deactivate(widgetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently removes the widget from WIDGET_MASTER, WIDGET_QUERY, WIDGET_CONFIG,
     * and WIDGET_PAYLOAD.  This operation cannot be undone.
     * Returns 204 No Content, or 404 if the widget is not found.
     */
    @DeleteMapping("/{widgetId}")
    public ResponseEntity<Void> remove(@PathVariable String widgetId) {
        widgetDefinitionService.remove(widgetId);
        return ResponseEntity.noContent().build();
    }
}
