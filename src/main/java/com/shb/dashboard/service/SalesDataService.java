package com.shb.dashboard.service;

import com.shb.dashboard.dao.SalesRecordDao;
import com.shb.dashboard.model.SalesRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SalesDataService {

    private final SalesRecordDao salesRecordDao;

    /** Injects the DAO that persists records into the SALES_SUMMARY target table. */
    public SalesDataService(SalesRecordDao salesRecordDao) {
        this.salesRecordDao = salesRecordDao;
    }

    /** Inserts a single sales record into SALES_SUMMARY. */
    public void add(SalesRecord record) {
        salesRecordDao.insert(record);
    }

    /**
     * Inserts multiple sales records into SALES_SUMMARY in a single JDBC batch.
     * Significantly faster than repeated {@link #add} calls for bulk seeding.
     */
    public void addBatch(List<SalesRecord> records) {
        salesRecordDao.insertBatch(records);
    }

    /** Returns all SALES_SUMMARY records ordered by sale_date descending, then by id. */
    public List<Map<String, Object>> getAll() {
        return salesRecordDao.findAll();
    }
}
