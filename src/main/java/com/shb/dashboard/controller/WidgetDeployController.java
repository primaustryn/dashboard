package com.shb.dashboard.controller;

import com.shb.dashboard.model.DeployResult;
import com.shb.dashboard.service.WidgetDeployService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * GitOps deploy endpoint.
 *
 * Accepts a raw YAML file (the same file committed to the widget registry repo)
 * and runs it through the full deployment pipeline:
 *   parse → validate → SQL dry-run → Base64 encode → persist to WIDGET_PAYLOAD.
 *
 * Typical GitOps CI invocation:
 * <pre>
 *   curl -s -X POST http://backend:8081/api/v1/admin/widgets/deploy \
 *        -H "Content-Type: text/plain" \
 *        --data-binary @widgets/WD_SALES_REGION.yml
 * </pre>
 *
 * This endpoint is intentionally separate from the legacy
 * {@link WidgetAdminController} (POST /api/v1/admin/widgets) so the two
 * storage strategies can coexist during the migration period.  Once all
 * widgets are on the GitOps path, the legacy endpoint can be retired.
 */
@RestController
@RequestMapping("/api/v1/admin/widgets")
public class WidgetDeployController {

    private final WidgetDeployService widgetDeployService;

    /** Injects the GitOps deployment pipeline service. */
    public WidgetDeployController(WidgetDeployService widgetDeployService) {
        this.widgetDeployService = widgetDeployService;
    }

    /**
     * Deploy a widget from a GitOps YAML definition.
     *
     * <p>Consumes {@code text/plain}, {@code application/yaml}, and
     * {@code application/x-yaml} so the endpoint accepts both:
     * <ul>
     *   <li>{@code curl --data-binary @file.yml -H "Content-Type: text/plain"}</li>
     *   <li>{@code curl --data-binary @file.yml -H "Content-Type: application/yaml"}</li>
     * </ul>
     *
     * <p>Returns:
     * <ul>
     *   <li>201 Created — widget deployed; {@code Location} header points to the
     *       widget data endpoint.</li>
     *   <li>400 Bad Request — malformed YAML or structural validation failure.</li>
     *   <li>422 Unprocessable Entity — YAML was valid but the embedded SQL failed
     *       the pre-flight dry-run against the target DB.</li>
     * </ul>
     */
    @PostMapping(
        value    = "/deploy",
        consumes = { MediaType.TEXT_PLAIN_VALUE, "application/yaml", "application/x-yaml" }
    )
    public ResponseEntity<DeployResult> deploy(@RequestBody String rawYaml) {
        DeployResult result = widgetDeployService.deploy(rawYaml);
        URI location = URI.create("/api/v1/widgets/" + result.widgetId());
        return ResponseEntity.created(location).body(result);
    }
}
