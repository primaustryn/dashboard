package com.shb.dashboard.dao;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class GenericRowDao {

    private static final java.util.regex.Pattern SAFE_IDENTIFIER =
            java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");

    private final NamedParameterJdbcTemplate targetTemplate;
    // Used only for INFORMATION_SCHEMA lookups — never for target data writes.
    private final JdbcTemplate targetJdbc;

    public GenericRowDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetTemplate = new NamedParameterJdbcTemplate(targetDataSource);
        this.targetJdbc     = new JdbcTemplate(targetDataSource);
    }

    public void insertRow(String tableName, Map<String, Object> row) {
        String table = normalizeAndValidate(tableName);
        row.keySet().forEach(this::validateIdentifier);
        verifyTableExists(table);
        verifyColumnsExist(table, row.keySet());

        String cols   = String.join(", ", row.keySet());
        String params = row.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        targetTemplate.update("INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")", row);
    }

    public void insertBatch(String tableName, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        String table = normalizeAndValidate(tableName);
        rows.get(0).keySet().forEach(this::validateIdentifier);
        verifyTableExists(table);
        verifyColumnsExist(table, rows.get(0).keySet());

        String cols   = String.join(", ", rows.get(0).keySet());
        String params = rows.get(0).keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        String sql    = "INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")";

        @SuppressWarnings("unchecked")
        Map<String, Object>[] paramArray = rows.toArray(new Map[0]);
        targetTemplate.batchUpdate(sql, paramArray);
    }

    public List<Map<String, Object>> findAll(String tableName) {
        String table = normalizeAndValidate(tableName);
        verifyTableExists(table);
        return targetTemplate.queryForList("SELECT * FROM " + table, Map.of());
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private String normalizeAndValidate(String name) {
        validateIdentifier(name);
        return name.toUpperCase();
    }

    private void validateIdentifier(String name) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: '" + name + "'");
        }
    }

    private void verifyTableExists(String upperTableName) {
        // H2 uses INFORMATION_SCHEMA.TABLES; Oracle/Tibero: USER_TABLES.
        Integer count = targetJdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ?",
            Integer.class, upperTableName);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Table not found: " + upperTableName);
        }
    }

    private void verifyColumnsExist(String upperTableName, Set<String> columnNames) {
        // H2 uses INFORMATION_SCHEMA.COLUMNS; Oracle/Tibero: USER_TAB_COLUMNS.
        List<String> existing = targetJdbc.queryForList(
            "SELECT UPPER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ?",
            String.class, upperTableName);
        Set<String> existingUpper = Set.copyOf(existing);
        for (String col : columnNames) {
            if (!existingUpper.contains(col.toUpperCase())) {
                throw new IllegalArgumentException(
                    "Column '" + col + "' does not exist in table " + upperTableName);
            }
        }
    }
}
