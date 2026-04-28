package com.shb.dashboard.dao;

import com.shb.dashboard.model.SalesRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.util.List;
import java.util.Map;

@Repository
public class SalesRecordDao {

    private final JdbcTemplate targetJdbcTemplate;

    /** Binds the target DataSource to a JdbcTemplate for SALES_SUMMARY DML operations. */
    public SalesRecordDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetJdbcTemplate = new JdbcTemplate(targetDataSource);
    }

    /**
     * Inserts a single {@link SalesRecord} into SALES_SUMMARY using positional parameters.
     * {@code saleDate} is converted from an ISO-8601 string to a {@link java.sql.Date}
     * before binding to avoid locale-dependent date parsing by the JDBC driver.
     */
    public void insert(SalesRecord record) {
        targetJdbcTemplate.update(
            "INSERT INTO SALES_SUMMARY (region, product, amount, sale_date)"
            + " VALUES (?, ?, ?, ?)",
            record.getRegion(),
            record.getProduct(),
            record.getAmount(),
            Date.valueOf(record.getSaleDate())
        );
    }

    /**
     * Batch-inserts multiple {@link SalesRecord} objects into SALES_SUMMARY in a single
     * JDBC batch operation.  Using a batch statement reduces round-trips to the DB compared
     * to calling {@link #insert} in a loop.
     */
    public void insertBatch(List<SalesRecord> records) {
        targetJdbcTemplate.batchUpdate(
            "INSERT INTO SALES_SUMMARY (region, product, amount, sale_date)"
            + " VALUES (?, ?, ?, ?)",
            records,
            records.size(),
            (ps, r) -> {
                ps.setString(1, r.getRegion());
                ps.setString(2, r.getProduct());
                ps.setBigDecimal(3, r.getAmount());
                ps.setDate(4, Date.valueOf(r.getSaleDate()));
            }
        );
    }

    /**
     * Returns all rows from SALES_SUMMARY as a list of column-name → value maps,
     * ordered by {@code sale_date} descending then {@code id} descending.
     */
    public List<Map<String, Object>> findAll() {
        return targetJdbcTemplate.queryForList(
            "SELECT id, region, product, amount, sale_date"
            + " FROM SALES_SUMMARY ORDER BY sale_date DESC, id DESC"
        );
    }
}
