package com.shb.dashboard.dao;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

@Repository
public class TargetSchemaDao {

    private final JdbcTemplate targetJdbcTemplate;

    public TargetSchemaDao(@Qualifier("targetDataSource") DataSource targetDataSource) {
        this.targetJdbcTemplate = new JdbcTemplate(targetDataSource);
    }

    public void execute(String sql) {
        targetJdbcTemplate.execute(sql);
    }
}
