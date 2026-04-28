package com.shb.dashboard.service;

import com.shb.dashboard.dao.GenericRowDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GenericDataService {

    private final GenericRowDao genericRowDao;

    /** Injects the DAO that validates identifiers and performs table-agnostic DML. */
    public GenericDataService(GenericRowDao genericRowDao) {
        this.genericRowDao = genericRowDao;
    }

    /**
     * Validates the table name and column names as safe SQL identifiers, confirms the schema,
     * then inserts a single row into the named target-DB table.
     */
    public void insertRow(String tableName, Map<String, Object> row) {
        genericRowDao.insertRow(tableName, row);
    }

    /**
     * Validates identifiers and schema once, then inserts all rows in a single JDBC batch.
     * No-op when {@code rows} is empty.
     */
    public void insertBatch(String tableName, List<Map<String, Object>> rows) {
        genericRowDao.insertBatch(tableName, rows);
    }

    /**
     * Returns all rows from the named target-DB table as a list of column-name → value maps.
     * Intended for data inspection and setup verification; not for paginated production reads.
     */
    public List<Map<String, Object>> getAll(String tableName) {
        return genericRowDao.findAll(tableName);
    }
}
