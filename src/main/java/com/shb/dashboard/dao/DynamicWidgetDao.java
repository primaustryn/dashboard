package com.shb.dashboard.dao;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Generic execution engine for target-DB queries.
 *
 * The DAO knows nothing about individual widgets or their result shapes.
 * NamedParameterJdbcTemplate is used exclusively to prevent SQL injection:
 * all caller-supplied values are bound as named parameters (":paramName"),
 * never interpolated directly into the SQL string.
 */
@Repository
public class DynamicWidgetDao {

    /**
     * Executes an arbitrary parameterised SELECT against the given DataSource.
     *
     * @param dataSource the resolved target DataSource for this widget
     * @param sql        the SQL from WIDGET_MASTER.query_sql (may contain :named params)
     * @param params     caller-supplied parameter values (from query-string / defaults)
     * @return           each row as a Map of columnName → value
     */
    public List<Map<String, Object>> executeTargetQuery(
            DataSource dataSource,
            String sql,
            Map<String, Object> params) {

        // A new template per call avoids shared state across datasources.
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(dataSource);
        return template.queryForList(sql, params);
    }
}
