package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shb.dashboard.dao.WidgetAuditDao;
import com.shb.dashboard.dao.WidgetPayloadDao;
import com.shb.dashboard.exception.SqlDryRunException;
import com.shb.dashboard.model.DeployResult;
import com.shb.dashboard.model.WidgetDeployRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * GitOps widget deployment pipeline.
 *
 * A single call to {@link #deploy(String)} executes the full deployment gate:
 *
 *   1. Parse   — SnakeYAML parses the YAML file into a typed request object.
 *   2. Validate — structural checks (required fields, widgetId format, etc.).
 *   3. Dry-Run  — widget SQL is executed as a zero-row probe against the target
 *                 DB; syntax errors or missing tables are caught here, before
 *                 any meta-DB write occurs.
 *   4. Encode   — SQL and uiSchema are Base64-encoded (charset-safe for Korean
 *                 / CJK content) then split into ≤4 000-char VARCHAR chunks.
 *   5. Persist  — chunks are atomically written to WIDGET_PAYLOAD.  Re-deploying
 *                 the same widgetId is fully idempotent.
 *
 * Why Base64?
 *   Chunking raw UTF-8 at a 4 000-byte boundary can split a multi-byte Korean
 *   or CJK codepoint across two rows.  Base64-encoding first transforms the
 *   content to 7-bit ASCII (every character is one byte), so the 4 000-char
 *   VARCHAR column fits exactly 3 000 raw source bytes per row — always safe.
 *
 * Why a SQL Dry-Run?
 *   Financial systems require a strict change-gate: a mis-typed table name or
 *   invalid column reference must be caught at deploy time, not at 3 AM when
 *   a widget fails to render for a risk officer.
 */
@Service
public class WidgetDeployService {

    private static final Logger log = LoggerFactory.getLogger(WidgetDeployService.class);

    private final JdbcTemplate            metaJdbc;
    private final WidgetPayloadDao         widgetPayloadDao;
    private final WidgetAuditDao           widgetAuditDao;
    private final Map<String, DataSource>  dataSourceRegistry;
    private final ObjectMapper             objectMapper;

    public WidgetDeployService(JdbcTemplate metaJdbc,
                               WidgetPayloadDao widgetPayloadDao,
                               WidgetAuditDao widgetAuditDao,
                               Map<String, DataSource> dataSourceRegistry,
                               ObjectMapper objectMapper) {
        this.metaJdbc           = metaJdbc;
        this.widgetPayloadDao   = widgetPayloadDao;
        this.widgetAuditDao     = widgetAuditDao;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper       = objectMapper;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Full GitOps deployment pipeline.  @Transactional covers only the meta-DB
     * writes (steps 4–5); the dry-run (step 3) reads from the target DB outside
     * a transaction, so a dry-run failure never needs a rollback.
     */
    @Transactional
    public DeployResult deploy(String rawYaml) {
        // ── 1. Parse ──────────────────────────────────────────────────────────
        WidgetDeployRequest req = parseYaml(rawYaml);

        // ── 2. Validate ───────────────────────────────────────────────────────
        validate(req);

        // ── 3. SQL Dry-Run (pre-flight) ───────────────────────────────────────
        // Throws SqlDryRunException on failure; nothing has been written yet.
        dryRunSql(req);

        // ── 4. Base64-encode payloads ─────────────────────────────────────────
        String encodedSql = encodeBase64(req.getSql().strip());

        String uiSchemaJson;
        try {
            uiSchemaJson = objectMapper.writeValueAsString(req.getUiSchema());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("uiSchema could not be serialized to JSON", ex);
        }
        String encodedUiSchema = encodeBase64(uiSchemaJson);

        // ── 5. Persist ────────────────────────────────────────────────────────
        upsertMaster(req.getWidgetId(), req.getTargetDb());
        int sqlChunks = widgetPayloadDao.replace(req.getWidgetId(), "SQL",       encodedSql);
        int uiChunks  = widgetPayloadDao.replace(req.getWidgetId(), "UI_SCHEMA", encodedUiSchema);
        widgetAuditDao.record(req.getWidgetId(), "DEPLOY");

        log.info("Widget [{}] deployed: {} SQL chunk(s), {} uiSchema chunk(s)",
                 req.getWidgetId(), sqlChunks, uiChunks);

        return new DeployResult(req.getWidgetId(), "DEPLOYED", sqlChunks, uiChunks);
    }

    // =========================================================================
    // Step 1 — YAML parsing
    // =========================================================================

    /**
     * Parses a GitOps YAML string into a WidgetDeployRequest.
     *
     * SnakeYAML is used directly (it is already on the Spring Boot classpath
     * via spring-boot-autoconfigure) to avoid adding the jackson-dataformat-yaml
     * dependency.  YAML block scalars (the {@code |} operator) are returned as
     * strings with embedded newlines — exactly what we need for the SQL field.
     */
    @SuppressWarnings("unchecked")
    private WidgetDeployRequest parseYaml(String rawYaml) {
        if (rawYaml == null || rawYaml.isBlank()) {
            throw new IllegalArgumentException("Deploy YAML payload is empty");
        }

        Map<String, Object> map;
        try {
            map = new Yaml().load(rawYaml);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed YAML: " + ex.getMessage(), ex);
        }

        if (map == null) {
            throw new IllegalArgumentException("YAML parsed to null — check file content");
        }

        return new WidgetDeployRequest(
            (String)              map.get("widgetId"),
            (String)              map.get("targetDb"),
            (String)              map.get("sql"),
            (Map<String, Object>) map.get("uiSchema")
        );
    }

    // =========================================================================
    // Step 2 — Structural validation
    // =========================================================================

    private void validate(WidgetDeployRequest req) {
        requireNonBlank(req.getWidgetId(), "widgetId");
        if (!req.getWidgetId().matches("[A-Z][A-Z0-9_]{1,49}")) {
            throw new IllegalArgumentException(
                "widgetId must be 2–50 uppercase alphanumeric/underscore characters "
                + "(e.g. WD_SALES_REGION); got: '" + req.getWidgetId() + "'");
        }

        requireNonBlank(req.getTargetDb(), "targetDb");
        if (!dataSourceRegistry.containsKey(req.getTargetDb())) {
            throw new IllegalArgumentException(
                "Unknown targetDb '" + req.getTargetDb() + "'. "
                + "Registered keys: " + dataSourceRegistry.keySet());
        }

        requireNonBlank(req.getSql(), "sql");
        if (req.getUiSchema() == null || req.getUiSchema().isEmpty()) {
            throw new IllegalArgumentException("uiSchema is required and must not be empty");
        }

        // Semantic vocabulary enforcement: require 'visualization' key.
        if (!req.getUiSchema().containsKey("visualization")) {
            throw new IllegalArgumentException(
                "uiSchema must contain 'visualization' key "
                + "(e.g. comparison | proportion | trend | distribution | "
                + "profile | utilization | correlation | hierarchy | geography)");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
    }

    // =========================================================================
    // Step 3 — SQL Dry-Run (pre-flight validation)
    // =========================================================================

    /**
     * Wraps the widget SQL in a zero-row subquery and executes it against the
     * target DB.  This validates syntax, table existence, and column references
     * without fetching any data.
     *
     * Named parameters (e.g. {@code :regionFilter}) are replaced with {@code NULL}
     * so the SQL is self-contained for the probe.
     *
     * The {@code SELECT * FROM (...) WHERE 1=0} wrapper has a useful security
     * property: DML statements (INSERT/UPDATE/DELETE) are not valid inside a
     * FROM subquery and will be rejected as syntax errors, effectively enforcing
     * that widget SQL must be a pure SELECT.
     */
    private void dryRunSql(WidgetDeployRequest req) {
        DataSource ds   = dataSourceRegistry.get(req.getTargetDb());
        JdbcTemplate probe = new JdbcTemplate(ds);
        probe.setQueryTimeout(10); // short — this is a validation probe, not live query

        String sql = req.getSql().strip();

        // Reject multi-statement payloads outright: a semicolon inside a subquery
        // is a syntax error on all target DBs and would produce a confusing message.
        if (sql.contains(";")) {
            throw new IllegalArgumentException(
                "Widget SQL must be a single SELECT statement. "
                + "Semicolons are not permitted in the 'sql' field.");
        }

        // Replace named params (:foo) with NULL so the SQL is syntactically complete.
        String paramFree  = sql.replaceAll(":\\w+", "NULL");
        String preflight  = "SELECT * FROM (" + paramFree + ") __preflight WHERE 1=0";

        try {
            probe.query(preflight, rs -> {});
            log.debug("SQL pre-flight passed for widget [{}]", req.getWidgetId());
        } catch (DataAccessException ex) {
            // getMostSpecificCause() unwraps Spring's exception wrapper to the raw
            // SQLException message, which contains the DB's actionable error text.
            throw new SqlDryRunException(
                req.getWidgetId(),
                ex.getMostSpecificCause().getMessage(),
                ex);
        }
    }

    // =========================================================================
    // Step 5 helpers — persistence
    // =========================================================================

    /**
     * INSERT-or-UPDATE WIDGET_MASTER.  Re-deploying an existing widget updates
     * its targetDb; is_active is left unchanged (deactivation is a separate op).
     */
    private void upsertMaster(String widgetId, String targetDb) {
        int updated = metaJdbc.update(
            "UPDATE WIDGET_MASTER SET target_db = ? WHERE widget_id = ?",
            targetDb, widgetId);
        if (updated == 0) {
            metaJdbc.update(
                "INSERT INTO WIDGET_MASTER (widget_id, target_db, is_active) VALUES (?, ?, TRUE)",
                widgetId, targetDb);
        }
    }

    // =========================================================================
    // Encoding utility
    // =========================================================================

    /**
     * Base64-encodes a UTF-8 string using the standard (non-URL) alphabet.
     * The result contains only printable ASCII — safe in any VARCHAR column.
     */
    private static String encodeBase64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
