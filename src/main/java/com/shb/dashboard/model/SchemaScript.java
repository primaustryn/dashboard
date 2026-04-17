package com.shb.dashboard.model;

public final class SchemaScript {

    private String sql;

    public SchemaScript() {}
    public SchemaScript(String sql) { this.sql = sql; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
}
