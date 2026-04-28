package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shb.dashboard.config.CacheConfig;
import com.shb.dashboard.dao.WidgetAuditDao;
import com.shb.dashboard.dao.WidgetPayloadDao;
import com.shb.dashboard.exception.SqlDryRunException;
import com.shb.dashboard.model.DeployResult;
import com.shb.dashboard.model.WidgetDeployRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;

/**
 * GitOps widget deployment pipeline.
 *
 * A single call to {@link #deploy(String)} executes the full deployment gate:
 *
 *   1. Parse    — SnakeYAML parses the YAML file into a typed request object.
 *   2. Validate — structural checks (required fields, widgetId format, etc.).
 *   3. Dry-Run  — widget SQL is executed as a zero-row probe against the Target DB;
 *                 syntax errors or missing tables are caught here, before any write.
 *   4. Cost     — EXPLAIN PLAN is executed against the Target DB optimizer. If the
 *                 query's estimated cost exceeds {@code MAX_QUERY_COST} the deploy is
 *                 rejected.  This is the Anti-DoS gate: it prevents a single badly
 *                 written widget from exhausting the Target DB connection pool at
 *                 runtime.
 *   5. Encode   — SQL and uiSchema are Base64-encoded then split into ≤ 4 000-char
 *                 VARCHAR chunks.
 *   6. Persist  — chunks are atomically written to WIDGET_PAYLOAD.  Re-deploying
 *                 the same widgetId is fully idempotent.
 *   7. Evict    — Redis cache entries for this widgetId are invalidated, but only
 *                 AFTER the DB transaction commits successfully (see below).
 *
 * <h3>Transaction-Synchronized cache eviction (step 7)</h3>
 *
 * Using {@code @CacheEvict} (even with {@code beforeInvocation=false}) is unsafe
 * here because Spring fires the eviction in the same thread, before the underlying
 * JDBC commit is guaranteed to be durable.  A crash or rollback between the
 * eviction and the commit would leave the cache empty while the DB still holds
 * the old data — the next request would re-prime Redis with stale metadata.
 *
 * {@link TransactionSynchronizationManager#registerSynchronization} lets us hook
 * into the Spring transaction lifecycle.  {@code afterCommit()} fires on the
 * commit thread <em>after</em> the JDBC driver has confirmed the write is durable,
 * and <em>never</em> fires on rollback.  This eliminates the race condition.
 */
@Service
public class WidgetDeployService {

    private static final Logger log = LoggerFactory.getLogger(WidgetDeployService.class);

    private final JdbcTemplate            metaJdbc;
    private final WidgetPayloadDao         widgetPayloadDao;
    private final WidgetAuditDao           widgetAuditDao;
    private final Map<String, DataSource>  dataSourceRegistry;
    private final ObjectMapper             objectMapper;
    private final CacheManager             cacheManager;

    /**
     * Oracle/Tibero optimizer cost ceiling.  Deployments whose root-node cost
     * exceeds this value are rejected before any DB write occurs.
     * Tunable via {@code dashboard.widget.deploy.max-query-cost} in
     * application.yml or as an environment variable {@code MAX_QUERY_COST}.
     */
    @Value("${dashboard.widget.deploy.max-query-cost:10000.0}")
    private double maxQueryCost;

    public WidgetDeployService(JdbcTemplate metaJdbc,
                               WidgetPayloadDao widgetPayloadDao,
                               WidgetAuditDao widgetAuditDao,
                               Map<String, DataSource> dataSourceRegistry,
                               ObjectMapper objectMapper,
                               CacheManager cacheManager) {
        this.metaJdbc           = metaJdbc;
        this.widgetPayloadDao   = widgetPayloadDao;
        this.widgetAuditDao     = widgetAuditDao;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper       = objectMapper;
        this.cacheManager       = cacheManager;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Full GitOps deployment pipeline.  @Transactional covers only the meta-DB
     * writes (steps 5–6); the dry-run and EXPLAIN PLAN (steps 3–4) read from the
     * Target DB outside a transaction, so their failures never need a rollback.
     */
    @Transactional
    public DeployResult deploy(String rawYaml) {
        // ── 1. Parse ──────────────────────────────────────────────────────────
        WidgetDeployRequest req = parseYaml(rawYaml);

        // ── 2. Validate ───────────────────────────────────────────────────────
        validate(req);

        // ── 3. SQL Dry-Run ────────────────────────────────────────────────────
        // Throws SqlDryRunException on failure; nothing has been written yet.
        dryRunSql(req);

        // ── 4. Query Cost Gate (Anti-DoS) ─────────────────────────────────────
        validateQueryCost(req);

        // ── 5. Base64-encode payloads ─────────────────────────────────────────
        String encodedSql = encodeBase64(req.getSql().strip());

        String uiSchemaJson;
        try {
            uiSchemaJson = objectMapper.writeValueAsString(req.getUiSchema());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("uiSchema could not be serialized to JSON", ex);
        }
        String encodedUiSchema = encodeBase64(uiSchemaJson);

        // ── 6. Persist ────────────────────────────────────────────────────────
        upsertMaster(req.getWidgetId(), req.getTargetDb());
        int sqlChunks = widgetPayloadDao.replace(req.getWidgetId(), "SQL",       encodedSql);
        int uiChunks  = widgetPayloadDao.replace(req.getWidgetId(), "UI_SCHEMA", encodedUiSchema);
        widgetAuditDao.record(req.getWidgetId(), "DEPLOY");

        // ── 7. Register post-commit cache eviction ────────────────────────────
        // The synchronization callback is registered while the transaction is
        // still open.  Spring calls afterCommit() on the same thread immediately
        // after the JDBC commit succeeds.  If the transaction rolls back for any
        // reason, afterCommit() is never invoked — no stale eviction occurs.
        registerPostCommitEviction(req.getWidgetId());

        log.info("Widget [{}] deploy persisted: {} SQL chunk(s), {} uiSchema chunk(s) — "
                 + "awaiting commit for cache eviction", req.getWidgetId(), sqlChunks, uiChunks);

        return new DeployResult(req.getWidgetId(), "DEPLOYED", sqlChunks, uiChunks);
    }

    // =========================================================================
    // Step 1 — YAML parsing
    // =========================================================================

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
        if (!req.getUiSchema().containsKey("visualization")) {
            throw new IllegalArgumentException(
                "uiSchema must contain 'visualization' key "
                + "(e.g. comparison | proportion | trend | distribution | "
                + "profile | utilization | correlation | hierarchy | geography | ohlc)");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
    }

    // =========================================================================
    // Step 3 — SQL Dry-Run (pre-flight syntax validation)
    // =========================================================================

    /**
     * Wraps the widget SQL in a zero-row subquery and executes it against the
     * Target DB to validate syntax, table existence, and column references.
     *
     * Named parameters (e.g. {@code :regionFilter}) are replaced with {@code NULL}
     * so the SQL is self-contained for the probe.
     *
     * The {@code SELECT * FROM (...) WHERE 1=0} wrapper has a useful security
     * property: DML statements (INSERT/UPDATE/DELETE) are not valid inside a
     * FROM subquery and are rejected as syntax errors, enforcing that widget
     * SQL must be a pure SELECT.
     */
    private void dryRunSql(WidgetDeployRequest req) {
        DataSource   ds    = dataSourceRegistry.get(req.getTargetDb());
        JdbcTemplate probe = new JdbcTemplate(ds);
        probe.setQueryTimeout(10);

        String sql = req.getSql().strip();
        if (sql.contains(";")) {
            throw new IllegalArgumentException(
                "Widget SQL must be a single SELECT statement. "
                + "Semicolons are not permitted in the 'sql' field.");
        }

        String paramFree = sql.replaceAll(":\\w+", "NULL");
        String preflight = "SELECT * FROM (" + paramFree + ") __preflight WHERE 1=0";

        try {
            probe.query(preflight, rs -> {});
            log.debug("SQL pre-flight passed for widget [{}]", req.getWidgetId());
        } catch (DataAccessException ex) {
            throw new SqlDryRunException(
                req.getWidgetId(),
                ex.getMostSpecificCause().getMessage(),
                ex);
        }
    }

    // =========================================================================
    // Step 4 — Query Cost Gate (Anti-DoS via EXPLAIN PLAN)
    // =========================================================================

    /**
     * Executes the database optimizer's EXPLAIN PLAN against the widget SQL and
     * rejects the deployment if the estimated cost exceeds {@link #maxQueryCost}.
     *
     * <h3>Why this matters for security</h3>
     * A widget whose SQL performs an unbounded full-table scan will issue that
     * scan on every user page load.  Under concurrent traffic this creates a
     * self-inflicted DoS on the Target DB connection pool.  Catching the problem
     * at deploy time — before any user ever loads the widget — prevents the
     * runtime blast radius entirely.
     *
     * <h3>Oracle / Tibero path</h3>
     * {@code EXPLAIN PLAN SET STATEMENT_ID = '<id>' FOR <sql>} populates the
     * session-level {@code PLAN_TABLE}.  The root row ({@code id = 0}) holds the
     * cumulative optimizer cost for the entire statement.  Both statements are
     * executed on the <em>same physical connection</em> (via
     * {@link SingleConnectionDataSource}) to guarantee session affinity —
     * required because {@code PLAN_TABLE} is session-scoped in Oracle.
     *
     * <h3>H2 path (dev)</h3>
     * H2's {@code EXPLAIN SELECT} returns a plan text string; there is no numeric
     * cost column.  The plan is logged at DEBUG and the threshold check is skipped
     * so local development is not blocked.
     */
    private void validateQueryCost(WidgetDeployRequest req) {
        DataSource ds      = dataSourceRegistry.get(req.getTargetDb());
        String     safeSql = req.getSql().strip().replaceAll(":\\w+", "NULL");

        // Both EXPLAIN PLAN FOR and the subsequent PLAN_TABLE query must run on
        // the same Oracle session.  Obtain a single connection and wrap it in
        // SingleConnectionDataSource(suppressClose=true) so the JdbcTemplate's
        // internal close calls do not return the connection to the pool prematurely.
        try (Connection conn = ds.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName().toUpperCase();
            DataSource singleConnDs = new SingleConnectionDataSource(conn, true);

            if (dbProduct.contains("ORACLE") || dbProduct.contains("TIBERO")) {
                enforceOracleCost(singleConnDs, req.getWidgetId(), safeSql);
            } else if (dbProduct.contains("H2")) {
                logH2ExplainPlan(singleConnDs, req.getWidgetId(), safeSql);
            } else {
                log.warn("[{}] Unrecognised DB product '{}' — EXPLAIN PLAN cost check skipped",
                         req.getWidgetId(), dbProduct);
            }
        } catch (SqlDryRunException ex) {
            throw ex; // re-throw cost-exceeded rejection as-is
        } catch (SQLException ex) {
            throw new IllegalStateException(
                "Failed to obtain DB connection for cost validation: " + ex.getMessage(), ex);
        }
    }

    /**
     * Oracle / Tibero cost enforcement.
     *
     * The STATEMENT_ID is derived from the widgetId (guaranteed uppercase
     * alphanumeric/underscore by the validate step) so it is safe to embed
     * directly in the DDL string without bind parameters.
     * PLAN_TABLE cleanup runs in a {@code finally} block to avoid polluting the
     * shared table with stale plans from partial failures.
     */
    private void enforceOracleCost(DataSource singleConnDs, String widgetId, String sql) {
        JdbcTemplate jdbc = new JdbcTemplate(singleConnDs);
        jdbc.setQueryTimeout(10);

        // Oracle STATEMENT_ID is limited to 30 chars in older versions.
        String stmtId = ("WD_" + widgetId).substring(0, Math.min(30, 3 + widgetId.length()));

        try {
            // Remove any stale plan from a previous failed deploy of the same widget.
            jdbc.update("DELETE FROM plan_table WHERE statement_id = ?", stmtId);

            // Populate PLAN_TABLE for the current session.
            // stmtId is validated alphanumeric/underscore above — embedding is safe.
            jdbc.execute("EXPLAIN PLAN SET STATEMENT_ID = '" + stmtId + "' FOR " + sql);

            // id = 0 is the root SELECT STATEMENT node whose COST column holds the
            // optimizer's total estimated cost for the entire query.
            Double cost = jdbc.queryForObject(
                "SELECT cost FROM plan_table WHERE statement_id = ? AND id = 0",
                Double.class, stmtId);

            if (cost == null) {
                log.warn("[{}] Optimizer returned NULL cost — table statistics may be stale. "
                         + "Run ANALYZE / DBMS_STATS before redeploying.", widgetId);
                return;
            }

            log.info("[{}] Optimizer cost: {} (threshold: {})", widgetId, cost, maxQueryCost);

            if (cost > maxQueryCost) {
                throw new SqlDryRunException(widgetId,
                    String.format(
                        "Query optimizer cost (%.0f) exceeds the allowed maximum (%.0f). "
                        + "Add an index on the filter columns or rewrite the query to avoid "
                        + "full table scans before redeploying.",
                        cost, maxQueryCost));
            }
        } finally {
            // Always clean up PLAN_TABLE — it is a shared, session-persistent table
            // in Oracle.  Leaving rows behind pollutes future EXPLAIN PLAN queries
            // from DBAs or monitoring tools.
            try {
                jdbc.update("DELETE FROM plan_table WHERE statement_id = ?", stmtId);
            } catch (DataAccessException cleanupEx) {
                log.warn("[{}] PLAN_TABLE cleanup failed (non-blocking): {}",
                         widgetId, cleanupEx.getMessage());
            }
        }
    }

    /**
     * H2 dev path — logs the explain plan text and skips numeric cost enforcement
     * because H2's {@code EXPLAIN} output does not include an optimizer cost column
     * comparable to Oracle's PLAN_TABLE.
     */
    private void logH2ExplainPlan(DataSource singleConnDs, String widgetId, String sql) {
        JdbcTemplate jdbc = new JdbcTemplate(singleConnDs);
        jdbc.setQueryTimeout(10);
        try {
            String plan = jdbc.queryForObject("EXPLAIN " + sql, String.class);
            log.debug("[{}] H2 EXPLAIN plan:\n{}", widgetId, plan);
        } catch (DataAccessException ex) {
            log.warn("[{}] H2 EXPLAIN failed (non-blocking): {}", widgetId, ex.getMessage());
        }
    }

    // =========================================================================
    // Step 7 — Transaction-Synchronized cache eviction
    // =========================================================================

    /**
     * Registers a {@link TransactionSynchronization} callback that evicts Redis
     * cache entries for {@code widgetId} after the meta-DB transaction commits.
     *
     * <h3>Why afterCommit() and not @CacheEvict?</h3>
     * Spring's {@code @CacheEvict(beforeInvocation=false)} fires in the
     * {@code afterReturning} advice phase, which is <em>inside</em> the open
     * transaction before the JDBC commit.  If the commit subsequently fails
     * (network timeout, rollback triggered by another thread, etc.) the cache
     * is already empty but the DB still holds the old data — the next cache miss
     * re-primes Redis with stale metadata for up to 24 hours.
     *
     * {@code TransactionSynchronization.afterCommit()} fires on the same thread
     * <em>after</em> the JDBC driver has acknowledged the commit and
     * <em>never</em> fires on rollback.  This is the only correct hook for
     * cache eviction that must be strictly causally-after a durable write.
     *
     * <h3>Spring 6 note</h3>
     * {@code TransactionSynchronizationAdapter} was removed in Spring 6.
     * {@link TransactionSynchronization} is now an interface with default methods;
     * we override only {@code afterCommit()}.
     */
    private void registerPostCommitEviction(String widgetId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("[{}] DB transaction committed — evicting Redis cache entries", widgetId);
                try {
                    evictWidgetCaches(widgetId);
                } catch (Exception ex) {
                    // Cache eviction is best-effort. If Redis is down the entries
                    // will expire naturally after their TTL rather than blocking deploys.
                    log.warn("[{}] Cache eviction failed (non-blocking) — entries expire by TTL: {}",
                             widgetId, ex.getMessage());
                }
            }
        });
    }

    private void evictWidgetCaches(String widgetId) {
        Cache metaCache = cacheManager.getCache(CacheConfig.METADATA_CACHE);
        Cache dataCache = cacheManager.getCache(CacheConfig.DATA_CACHE);
        if (metaCache != null) metaCache.evict(widgetId);
        if (dataCache != null) dataCache.evict(widgetId);
        log.debug("[{}] Evicted '{}' and '{}' cache entries",
                  widgetId, CacheConfig.METADATA_CACHE, CacheConfig.DATA_CACHE);
    }

    // =========================================================================
    // Step 6 helpers — persistence
    // =========================================================================

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

    private static String encodeBase64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
