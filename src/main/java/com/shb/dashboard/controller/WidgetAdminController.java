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

    public WidgetAdminController(WidgetDefinitionService widgetDefinitionService) {
        this.widgetDefinitionService = widgetDefinitionService;
    }

    @PostMapping
    public ResponseEntity<Void> register(@RequestBody WidgetDefinition def) {
        widgetDefinitionService.register(def);
        return ResponseEntity.created(URI.create("/api/v1/widgets/" + def.getWidgetId())).build();
    }

    @GetMapping
    public ResponseEntity<List<WidgetDefinition>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<WidgetDefinition> result = activeOnly
                ? widgetDefinitionService.listActive()
                : widgetDefinitionService.listAll();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{widgetId}")
    public ResponseEntity<Void> update(
            @PathVariable String widgetId,
            @RequestBody WidgetDefinition def) {
        widgetDefinitionService.update(widgetId, def);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{widgetId}/activate")
    public ResponseEntity<Void> activate(@PathVariable String widgetId) {
        widgetDefinitionService.activate(widgetId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{widgetId}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String widgetId) {
        widgetDefinitionService.deactivate(widgetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{widgetId}")
    public ResponseEntity<Void> remove(@PathVariable String widgetId) {
        widgetDefinitionService.remove(widgetId);
        return ResponseEntity.noContent().build();
    }
}
