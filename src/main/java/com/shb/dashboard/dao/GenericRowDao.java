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

    /** Builds a NamedParameterJdbcTemplate and a plain JdbcTemplate over the target DataSource. */
    public GenericRowDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetTemplate = new NamedParameterJdbcTemplate(targetDataSource);
        this.targetJdbc     = new JdbcTemplate(targetDataSource);
    }

    /**
     * Validates the table name and all column names as safe SQL identifiers, confirms the table
     * and columns exist in INFORMATION_SCHEMA, then inserts the row using named parameters.
     * Throws {@link IllegalArgumentException} on any validation failure.
     */
    public void insertRow(String tableName, Map<String, Object> row) {
        String table = normalizeAndValidate(tableName);
        row.keySet().forEach(this::validateIdentifier);
        verifyTableExists(table);
        verifyColumnsExist(table, row.keySet());

        String cols   = String.join(", ", row.keySet());
        String params = row.keySet().stream().map(k -> ":" + k).collect(Collectors.joining(", "));
        targetTemplate.update("INSERT INTO " + table + " (" + cols + ") VALUES (" + params + ")", row);
    }

    /**
     * Validates identifiers and schema once using the first row's keys, then batch-inserts all
     * rows in a single JDBC batch statement for efficiency.
     * Does nothing if {@code rows} is empty.
     */
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

    /**
     * Verifies the table exists, then returns all rows as a list of column-name → value maps.
     * Intended for data inspection; not paginated.
     */
    public List<Map<String, Object>> findAll(String tableName) {
        String table = normalizeAndValidate(tableName);
        verifyTableExists(table);
        return targetTemplate.queryForList("SELECT * FROM " + table, Map.of());
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    /**
     * Upper-cases the identifier and validates it against the safe-identifier pattern.
     * Returns the normalized (upper-cased) name for use in SQL strings.
     */
    private String normalizeAndValidate(String name) {
        validateIdentifier(name);
        return name.toUpperCase();
    }

    /**
     * Throws {@link IllegalArgumentException} if the name is null or contains characters
     * that are not safe for embedding in a SQL identifier position (letters, digits, underscore;
     * must start with a letter).  This guards against SQL injection via dynamic table/column names.
     */
    private void validateIdentifier(String name) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: '" + name + "'");
        }
    }

    /**
     * Queries {@code INFORMATION_SCHEMA.TABLES} to confirm the table exists before any DML
     * is issued.  Throws {@link IllegalArgumentException} if not found.
     * Uses H2-compatible INFORMATION_SCHEMA syntax; compatible with Oracle/Tibero views at production.
     */
    private void verifyTableExists(String upperTableName) {
        Integer count = targetJdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = ?",
            Integer.class, upperTableName);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Table not found: " + upperTableName);
        }
    }

    /**
     * Queries {@code INFORMATION_SCHEMA.COLUMNS} to confirm that every requested column exists
     * in the target table.  Throws {@link IllegalArgumentException} naming the first missing column.
     * Prevents silent data loss from mis-spelled column names in the request payload.
     */
    private void verifyColumnsExist(String upperTableName, Set<String> columnNames) {
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
