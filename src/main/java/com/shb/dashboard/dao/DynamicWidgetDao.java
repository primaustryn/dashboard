package com.shb.dashboard.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Executes dynamic widget SQL against the Target DB with hard safety limits.
 *
 * <h3>Safety contract</h3>
 * <pre>
 *  ┌──────────────────────┬────────────────────────────────────────────────────┐
 *  │ Guard                │ Rationale                                          │
 *  ├──────────────────────┼────────────────────────────────────────────────────┤
 *  │ Query timeout 30 s   │ Prevents a runaway analytics query from holding a  │
 *  │                      │ Target DB connection indefinitely.  The JDBC driver │
 *  │                      │ issues a statement cancel and throws a             │
 *  │                      │ QueryTimeoutException which propagates as HTTP 500. │
 *  ├──────────────────────┼────────────────────────────────────────────────────┤
 *  │ Row cap 10 000 + 1   │ Fetching MAX_ROWS + 1 detects truncation without   │
 *  │                      │ pulling the full result set into JVM heap.  If the │
 *  │                      │ +1 sentinel row is present the result is marked     │
 *  │                      │ truncated=true; the caller surfaces this to the UI. │
 *  ├──────────────────────┼────────────────────────────────────────────────────┤
 *  │ Named-param binding  │ NamedParameterJdbcTemplate binds values via JDBC   │
 *  │                      │ PreparedStatement — SQL injection via the params    │
 *  │                      │ map is structurally impossible.                     │
 *  ├──────────────────────┼────────────────────────────────────────────────────┤
 *  │ DML rejection        │ The dry-run gate in WidgetDeployService already     │
 *  │                      │ rejects non-SELECT statements at deploy time via    │
 *  │                      │ the SELECT * FROM (...) WHERE 1=0 wrapper.          │
 *  │                      │ This DAO is the runtime executor — it trusts the    │
 *  │                      │ deploy gate and does not re-validate.               │
 *  └──────────────────────┴────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Observability</h3>
 * Execution time is logged at DEBUG level.  Because {@code WidgetEngineService}
 * injects {@code widgetId} into the MDC before calling this DAO, every log line
 * emitted here automatically carries the widget context in appenders configured
 * with {@code %X{widgetId}}.
 */
@Repository
public class DynamicWidgetDao {

    private static final Logger log = LoggerFactory.getLogger(DynamicWidgetDao.class);

    /** Abort the query if the Target DB has not responded within this window. */
    public static final int QUERY_TIMEOUT_SECONDS = 30;

    /**
     * Maximum rows returned to the caller.  A query that returns more rows is
     * truncated and flagged; the caller is responsible for surfacing the warning.
     */
    public static final int MAX_ROWS = 10_000;

    /**
     * Executes a parameterised SELECT against the given {@code dataSource} and
     * returns up to {@link #MAX_ROWS} rows.
     *
     * <p>A new {@link NamedParameterJdbcTemplate} is created per call rather than
     * cached as a field because the Target DataSource is resolved at runtime from
     * the registry and can differ between widgets.  Template construction is
     * lightweight (it wraps the pool, not a connection).
     *
     * @param dataSource live DataSource for the widget's target DB
     * @param sql        widget SQL, possibly containing {@code :namedParams}
     * @param params     runtime filter values; use an empty map for static queries
     * @return           query result with a truncation flag
     */
    public QueryResult executeTargetQuery(
            DataSource dataSource,
            String sql,
            Map<String, Object> params) {

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        // Fetch MAX_ROWS + 1 so we can detect whether the full result was returned
        // without loading all rows into heap when the result set is large.
        jdbc.getJdbcTemplate().setMaxRows(MAX_ROWS + 1);

        long startNs = System.nanoTime();
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        boolean truncated = rows.size() > MAX_ROWS;
        if (truncated) {
            rows = rows.subList(0, MAX_ROWS);
        }

        // widgetId is in MDC (injected by WidgetEngineService) — included automatically.
        log.debug("Target DB query: {}ms, {} row(s), truncated={}", elapsedMs, rows.size(), truncated);

        return new QueryResult(rows, truncated);
    }

    /**
     * Value object returned by {@link #executeTargetQuery}.
     *
     * @param rows      up to {@link DynamicWidgetDao#MAX_ROWS} result rows
     * @param truncated {@code true} if the query produced more rows than the cap;
     *                  the caller should surface this as a UI warning
     */
    public record QueryResult(List<Map<String, Object>> rows, boolean truncated) {}
}
