package com.shb.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shb.dashboard.config.CacheConfig;
import com.shb.dashboard.dao.DynamicWidgetDao;
import com.shb.dashboard.dao.DynamicWidgetDao.QueryResult;
import com.shb.dashboard.dao.WidgetPayloadDao;
import com.shb.dashboard.exception.WidgetNotFoundException;
import com.shb.dashboard.model.WidgetMeta;
import com.shb.dashboard.model.WidgetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Widget query engine — resolves a widgetId to executable SQL + uiSchema and
 * returns the rendered data payload to the frontend.
 *
 * <h3>Caching strategy</h3>
 * Cache operations are performed manually via {@link CacheManager} rather than
 * via {@code @Cacheable} annotations.  The reason: both {@code fetchCachedMeta}
 * and {@code fetchCachedData} are called from {@link #getWidgetData}, which lives
 * on the same bean instance.  Spring AOP proxies only intercept <em>external</em>
 * calls; a {@code this.} reference bypasses the proxy and therefore
 * {@code @Cacheable} would silently never fire.  Direct {@code CacheManager} usage
 * avoids the self-invocation pitfall without requiring a separate bean or
 * {@code @Lazy} self-injection.
 *
 * <h3>MDC injection</h3>
 * {@code widgetId} is placed into the Mapped Diagnostic Context at the start of
 * every request so that every log line emitted downstream (DAO, connection pool,
 * etc.) automatically includes the widget context.  The {@code finally} block
 * guarantees removal even if an exception propagates.  Logback / Log4j2 patterns
 * should include {@code %X{widgetId}} to surface this field.
 *
 * <h3>Storage strategy (dual-path for zero-downtime migration)</h3>
 * Primary path (GitOps / WIDGET_PAYLOAD): SQL and uiSchema are Base64-chunked.
 * Fallback path (legacy EAV / WIDGET_QUERY + WIDGET_CONFIG): pre-GitOps widgets.
 */
@Service
public class WidgetEngineService {

    private static final Logger log = LoggerFactory.getLogger(WidgetEngineService.class);

    private final JdbcTemplate            metaJdbcTemplate;
    private final WidgetPayloadDao         widgetPayloadDao;
    private final DynamicWidgetDao         dynamicWidgetDao;
    private final Map<String, DataSource>  dataSourceRegistry;
    private final ObjectMapper             objectMapper;
    private final CacheManager             cacheManager;

    public WidgetEngineService(JdbcTemplate metaJdbcTemplate,
                               WidgetPayloadDao widgetPayloadDao,
                               DynamicWidgetDao dynamicWidgetDao,
                               Map<String, DataSource> dataSourceRegistry,
                               ObjectMapper objectMapper,
                               CacheManager cacheManager) {
        this.metaJdbcTemplate   = metaJdbcTemplate;
        this.widgetPayloadDao   = widgetPayloadDao;
        this.dynamicWidgetDao   = dynamicWidgetDao;
        this.dataSourceRegistry = dataSourceRegistry;
        this.objectMapper       = objectMapper;
        this.cacheManager       = cacheManager;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Resolves and executes a widget, returning the combined uiSchema + data
     * payload for the frontend.
     *
     * <h3>Observability</h3>
     * {@code widgetId} is injected into the MDC so every downstream log line
     * (DAO, JDBC, Lettuce) automatically carries the widget context.
     * Execution time is logged at INFO level to feed dashboards and alert rules
     * that track per-widget latency across distributed nodes.
     */
    public WidgetResponse getWidgetData(String widgetId, Map<String, Object> params) {
        // ── MDC injection ─────────────────────────────────────────────────────
        // Put widgetId into the MDC before any work so all downstream log
        // statements automatically include it via %X{widgetId} in the pattern.
        MDC.put("widgetId", widgetId);
        long startMs = System.currentTimeMillis();

        try {
            // ── 1. Metadata resolution (Redis → meta-DB) ──────────────────────
            WidgetMeta meta     = fetchCachedMeta(widgetId);
            JsonNode   uiSchema = parseUiSchema(widgetId, meta.dynamicConfig());
            DataSource targetDs = resolveDataSource(meta.targetDb());

            // ── 2. Data query (Redis → Target DB) ─────────────────────────────
            QueryResult result = fetchCachedData(widgetId, targetDs, meta.querySql(), params);

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[{}] Completed in {}ms | truncated={}", widgetId, elapsedMs, result.truncated());

            return new WidgetResponse(widgetId, uiSchema, result.rows(), result.truncated());

        } finally {
            // Unconditional cleanup: MDC is thread-local state on a pooled request
            // thread.  If not removed, the widgetId leaks into the next request
            // handled by the same thread.
            MDC.remove("widgetId");
        }
    }

    // =========================================================================
    // Metadata — Redis-first resolution
    // =========================================================================

    /**
     * Returns widget metadata from the Redis {@code widgetMetadataCache} (TTL 24 h)
     * on a cache hit, or loads from the meta-DB and populates the cache on a miss.
     *
     * <h3>Concurrency note</h3>
     * Two simultaneous cache misses for the same widgetId will both query the DB and
     * both call {@code cache.put()}.  This is intentional (no locking): widget metadata
     * is immutable between deploys, so both threads read identical rows.  The second
     * {@code put} is a harmless overwrite of an equivalent value.  Adding a distributed
     * lock would introduce latency and a failure mode for a negligible gain.
     */
    private WidgetMeta fetchCachedMeta(String widgetId) {
        Cache metaCache = cacheManager.getCache(CacheConfig.METADATA_CACHE);

        if (metaCache != null) {
            try {
                Cache.ValueWrapper hit = metaCache.get(widgetId);
                if (hit != null) {
                    log.debug("[{}] widgetMetadataCache HIT", widgetId);
                    return (WidgetMeta) hit.get();
                }
            } catch (Exception ex) {
                log.warn("[{}] widgetMetadataCache read failed — falling through to meta-DB: {}",
                         widgetId, ex.getMessage());
            }
        }

        log.debug("[{}] widgetMetadataCache MISS — loading from meta-DB", widgetId);
        WidgetMeta meta = loadWidgetMetaFromDb(widgetId);

        if (metaCache != null) {
            try {
                metaCache.put(widgetId, meta);
            } catch (Exception ex) {
                log.warn("[{}] widgetMetadataCache write failed — result not cached: {}",
                         widgetId, ex.getMessage());
            }
        }
        return meta;
    }

    // =========================================================================
    // Data — Redis-first query execution
    // =========================================================================

    /**
     * Returns query results from the Redis {@code widgetDataCache} (TTL 5 min) on a
     * cache hit, or executes against the Target DB and populates the cache on a miss.
     *
     * <h3>Truncated results are never cached</h3>
     * Truncation ({@code rows.size() > MAX_ROWS}) signals that the query returned
     * more data than the engine allows.  Caching truncated data would hide the
     * underlying data quality issue from operators and serve permanently incomplete
     * results until TTL expiry.  We let it fall through to a live query every time
     * so the truncation warning is always visible in logs.
     *
     * <h3>Parameterised cache keys</h3>
     * When {@code params} is non-empty (e.g. a region filter), each distinct param
     * set gets its own cache slot derived from a stable, sorted string representation.
     * Using {@code params.hashCode()} alone is unsafe: hash collisions between
     * different param sets would serve the wrong cached rows.
     *
     * <h3>Target DB protection</h3>
     * With 5-minute TTL and a moderate widget count, a 100-node cluster under
     * continuous traffic will generate at most one Target DB query per widget per
     * 5 minutes instead of one per request — a >99% reduction in Target DB load.
     */
    private QueryResult fetchCachedData(String widgetId,
                                        DataSource ds,
                                        String sql,
                                        Map<String, Object> params) {
        Cache  dataCache = cacheManager.getCache(CacheConfig.DATA_CACHE);
        String cacheKey  = buildDataCacheKey(widgetId, params);

        if (dataCache != null) {
            try {
                Cache.ValueWrapper hit = dataCache.get(cacheKey);
                if (hit != null) {
                    log.debug("[{}] widgetDataCache HIT (key={})", widgetId, cacheKey);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) hit.get();
                    return new QueryResult(rows, false);
                }
            } catch (Exception ex) {
                log.warn("[{}] widgetDataCache read failed — falling through to Target DB: {}",
                         widgetId, ex.getMessage());
            }
        }

        log.debug("[{}] widgetDataCache MISS — querying Target DB", widgetId);
        QueryResult result = dynamicWidgetDao.executeTargetQuery(ds, sql, params);

        if (dataCache != null && !result.truncated()) {
            try {
                dataCache.put(cacheKey, result.rows());
            } catch (Exception ex) {
                log.warn("[{}] widgetDataCache write failed — result not cached: {}",
                         widgetId, ex.getMessage());
            }
        }

        if (result.truncated()) {
            log.warn("[{}] Result truncated to {} rows — not caching truncated data",
                     widgetId, DynamicWidgetDao.MAX_ROWS);
        }

        return result;
    }

    /**
     * Produces a stable, collision-safe cache key.
     *
     * Empty params → key is the widgetId alone.
     * Non-empty params → key is {@code widgetId:key1=val1&key2=val2} with entries
     * sorted alphabetically so that maps with identical content but different
     * insertion order always produce the same key.
     */
    private static String buildDataCacheKey(String widgetId, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return widgetId;
        String paramSig = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return widgetId + ":" + paramSig;
    }

    // =========================================================================
    // Meta resolution — dual-path DB load (called only on cache miss)
    // =========================================================================

    private WidgetMeta loadWidgetMetaFromDb(String widgetId) {
        String targetDb  = loadTargetDb(widgetId);
        String sqlBase64 = widgetPayloadDao.loadAssembled(widgetId, "SQL");

        if (sqlBase64 != null && !sqlBase64.isBlank()) {
            return loadFromPayloadTable(widgetId, targetDb, sqlBase64);
        }

        log.debug("[{}] Not in WIDGET_PAYLOAD — using legacy EAV tables", widgetId);
        return loadFromLegacyTables(widgetId, targetDb);
    }

    // ── Primary path (GitOps / WIDGET_PAYLOAD) ────────────────────────────────

    private WidgetMeta loadFromPayloadTable(String widgetId, String targetDb, String sqlBase64) {
        String querySql = decodeBase64(sqlBase64);

        String uiSchemaBase64 = widgetPayloadDao.loadAssembled(widgetId, "UI_SCHEMA");
        if (uiSchemaBase64 == null || uiSchemaBase64.isBlank()) {
            throw new IllegalStateException(
                "Widget [" + widgetId + "] has a SQL payload but no UI_SCHEMA payload. "
                + "Re-deploy the widget via POST /api/v1/admin/widgets/deploy.");
        }
        String dynamicConfig = decodeBase64(uiSchemaBase64);

        log.debug("[{}] Loaded from WIDGET_PAYLOAD (GitOps path)", widgetId);
        return new WidgetMeta(widgetId, targetDb, querySql, dynamicConfig);
    }

    // ── Legacy fallback path (pre-GitOps EAV tables) ─────────────────────────

    /**
     * @deprecated Migrate widgets to the GitOps deploy endpoint and remove this
     *             method along with WIDGET_QUERY / WIDGET_CONFIG tables.
     */
    @Deprecated
    private WidgetMeta loadFromLegacyTables(String widgetId, String targetDb) {
        List<String> sqlChunks = metaJdbcTemplate.queryForList(
            "SELECT chunk_text FROM WIDGET_QUERY WHERE widget_id = ? ORDER BY chunk_order",
            String.class, widgetId);
        String querySql = String.join("", sqlChunks);

        List<Map<String, Object>> configRows = metaJdbcTemplate.queryForList(
            "SELECT config_key, config_val FROM WIDGET_CONFIG"
            + " WHERE widget_id = ? ORDER BY config_key",
            widgetId);
        ObjectNode configNode = objectMapper.createObjectNode();
        for (Map<String, Object> row : configRows) {
            String key = (String) row.get("CONFIG_KEY");
            String val = (String) row.get("CONFIG_VAL");
            try {
                configNode.set(key, objectMapper.readTree(val));
            } catch (JsonProcessingException ex) {
                configNode.put(key, val);
            }
        }
        return new WidgetMeta(widgetId, targetDb, querySql, configNode.toString());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String loadTargetDb(String widgetId) {
        try {
            return metaJdbcTemplate.queryForObject(
                "SELECT target_db FROM WIDGET_MASTER WHERE widget_id = ? AND is_active = TRUE",
                String.class, widgetId);
        } catch (EmptyResultDataAccessException ex) {
            throw new WidgetNotFoundException(widgetId);
        }
    }

    private static String decodeBase64(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private JsonNode parseUiSchema(String widgetId, String dynamicConfig) {
        try {
            return objectMapper.readTree(dynamicConfig);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                "Reconstructed uiSchema is not valid JSON for widget: " + widgetId, ex);
        }
    }

    private DataSource resolveDataSource(String targetDb) {
        DataSource ds = dataSourceRegistry.get(targetDb);
        if (ds == null) {
            throw new IllegalArgumentException(
                "No DataSource registered for target_db key: '" + targetDb + "'. "
                + "Add an entry to DataSourceConfig#dataSourceRegistry.");
        }
        return ds;
    }
}
