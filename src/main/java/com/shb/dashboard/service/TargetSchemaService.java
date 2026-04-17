package com.shb.dashboard.service;

import com.shb.dashboard.dao.TargetSchemaDao;
import org.springframework.stereotype.Service;

@Service
public class TargetSchemaService {

    private final TargetSchemaDao targetSchemaDao;

    public TargetSchemaService(TargetSchemaDao targetSchemaDao) {
        this.targetSchemaDao = targetSchemaDao;
    }

    public void execute(String sql) {
        targetSchemaDao.execute(sql);
    }
}
