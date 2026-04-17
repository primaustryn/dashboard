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

    public SalesRecordDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetJdbcTemplate = new JdbcTemplate(targetDataSource);
    }

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

    public List<Map<String, Object>> findAll() {
        return targetJdbcTemplate.queryForList(
            "SELECT id, region, product, amount, sale_date"
            + " FROM SALES_SUMMARY ORDER BY sale_date DESC, id DESC"
        );
    }
}
