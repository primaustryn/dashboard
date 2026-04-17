package com.shb.dashboard.service;

import com.shb.dashboard.dao.SalesRecordDao;
import com.shb.dashboard.model.SalesRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SalesDataService {

    private final SalesRecordDao salesRecordDao;

    public SalesDataService(SalesRecordDao salesRecordDao) {
        this.salesRecordDao = salesRecordDao;
    }

    public void add(SalesRecord record) {
        salesRecordDao.insert(record);
    }

    public void addBatch(List<SalesRecord> records) {
        salesRecordDao.insertBatch(records);
    }

    public List<Map<String, Object>> getAll() {
        return salesRecordDao.findAll();
    }
}
