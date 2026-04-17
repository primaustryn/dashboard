package com.shb.dashboard.dao;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class GenericRowDao {

    private static final java.util.regex.Pattern SAFE_IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");

    private final NamedParameterJdbcTemplate targetTemplate;

    public GenericRowDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetTemplate = new NamedParameterJdbcTemplate(targetDataSource);
    }

    public void insertRow(String tableName, Map<String, Object> row) {
        validateIdentifier(tableName);
        row.keySet().forEach(this::validateIdentifier);

        String cols   = String.join(", ", row.keySet());
        String params = row.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        String sql    = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + params + ")";

        targetTemplate.update(sql, row);
    }

    public void insertBatch(String tableName, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        validateIdentifier(tableName);
        rows.get(0).keySet().forEach(this::validateIdentifier);

        String cols   = String.join(", ", rows.get(0).keySet());
        String params = rows.get(0).keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        String sql    = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + params + ")";

        @SuppressWarnings("unchecked")
        Map<String, Object>[] paramArray = rows.toArray(new Map[0]);
        targetTemplate.batchUpdate(sql, paramArray);
    }

    public List<Map<String, Object>> findAll(String tableName) {
        validateIdentifier(tableName);
        return targetTemplate.queryForList("SELECT * FROM " + tableName, Map.of());
    }

    private void validateIdentifier(String name) {
        if (!SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: '" + name + "'");
        }
    }
}
