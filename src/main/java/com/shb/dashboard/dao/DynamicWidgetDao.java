package com.shb.dashboard.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Repository
public class DynamicWidgetDao {

    // Hard limits — protect DB connection pool and JVM heap from runaway queries.
    public static final int QUERY_TIMEOUT_SECONDS = 30;
    public static final int MAX_ROWS = 10_000;

    public QueryResult executeTargetQuery(
            DataSource dataSource,
            String sql,
            Map<String, Object> params) {

        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        // Fetch one extra row to detect truncation without pulling all rows into memory.
        template.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        template.getJdbcTemplate().setMaxRows(MAX_ROWS + 1);

        List<Map<String, Object>> rows = template.queryForList(sql, params);

        boolean truncated = rows.size() > MAX_ROWS;
        if (truncated) {
            rows = rows.subList(0, MAX_ROWS);
        }
        return new QueryResult(rows, truncated);
    }

    public record QueryResult(List<Map<String, Object>> rows, boolean truncated) {}
}
