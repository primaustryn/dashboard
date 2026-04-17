package com.shb.dashboard.service;

import com.shb.dashboard.dao.GenericRowDao;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GenericDataService {

    private final GenericRowDao genericRowDao;

    public GenericDataService(GenericRowDao genericRowDao) {
        this.genericRowDao = genericRowDao;
    }

    public void insertRow(String tableName, Map<String, Object> row) {
        genericRowDao.insertRow(tableName, row);
    }

    public void insertBatch(String tableName, List<Map<String, Object>> rows) {
        genericRowDao.insertBatch(tableName, rows);
    }

    public List<Map<String, Object>> getAll(String tableName) {
        return genericRowDao.findAll(tableName);
    }
}
