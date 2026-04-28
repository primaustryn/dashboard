package com.shb.dashboard.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Two-datasource configuration:
 *
 *   metaDataSource   - holds WIDGET_MASTER (widget definitions, SQL, UI schema).
 *   targetDataSource - holds the actual business data widgets query against.
 *
 * Pattern used: DataSourceProperties + initializeDataSourceBuilder().
 * This is the Spring Boot-recommended approach for multiple datasources because
 * DataSourceProperties correctly maps the generic 'url' property to each
 * connection pool's specific setter (e.g., HikariCP needs 'jdbcUrl', not 'url').
 * Using @ConfigurationProperties directly on a DataSource bean bypasses that
 * mapping and causes "jdbcUrl is required" errors with HikariCP.
 */
@Configuration
public class DataSourceConfig {

    // ── Meta DB ───────────────────────────────────────────────────────────────

    /** Binds the {@code spring.datasource.meta.*} properties to a typed properties object. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.meta")
    public DataSourceProperties metaDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Builds the meta-DB connection pool (HikariCP) from the bound properties.
     * Declared {@code @Primary} so that Spring's auto-configured {@link org.springframework.jdbc.core.JdbcTemplate}
     * targets the meta-DB by default, without requiring an explicit {@code @Qualifier} on every injection point.
     */
    @Bean("metaDataSource")
    @Primary
    public DataSource metaDataSource(
            @Qualifier("metaDataSourceProperties") DataSourceProperties props) {
        // initializeDataSourceBuilder() translates props.url -> HikariCP jdbcUrl, etc.
        return props.initializeDataSourceBuilder().build();
    }

    /**
     * Runs {@code db/meta/schema.sql} against the meta-DB on application startup to create
     * WIDGET_MASTER, WIDGET_QUERY, WIDGET_CONFIG, WIDGET_PAYLOAD, WIDGET_AUDIT, and AUDIT_LOG.
     * The script is written to be idempotent (CREATE TABLE IF NOT EXISTS) so re-runs are safe.
     */
    @Bean
    public DataSourceInitializer metaDataSourceInitializer(
            @Qualifier("metaDataSource") DataSource ds) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/meta/schema.sql"));

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(ds);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    // ── Target DB ─────────────────────────────────────────────────────────────

    /** Binds the {@code spring.datasource.target.*} properties to a typed properties object. */
    @Bean
    @ConfigurationProperties("spring.datasource.target")
    public DataSourceProperties targetDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** Builds the target-DB connection pool from the bound properties. */
    @Bean("targetDataSource")
    public DataSource targetDataSource(
            @Qualifier("targetDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

    /**
     * Runs {@code db/target/schema.sql} against the target-DB on startup to create
     * business-data tables (SALES_SUMMARY, TRADE_SUMMARY, FX_OHLC_DAILY, etc.).
     * The script is idempotent so re-running the application never drops existing data.
     */
    @Bean
    public DataSourceInitializer targetDataSourceInitializer(
            @Qualifier("targetDataSource") DataSource ds) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/target/schema.sql"));

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(ds);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    // ── DataSource Registry ───────────────────────────────────────────────────
    // Maps the target_db column value in WIDGET_MASTER to a live DataSource.
    // Adding a new target = one new entry here; no other code changes required.

    /**
     * Returns a registry that maps {@code target_db} key strings (as stored in WIDGET_MASTER)
     * to live DataSource instances.  Widget deploy and engine services look up the correct
     * DataSource at runtime using this registry, keeping widget definitions decoupled from
     * connection-pool wiring.
     */
    @Bean
    public Map<String, DataSource> dataSourceRegistry(
            @Qualifier("targetDataSource") DataSource targetDs) {
        Map<String, DataSource> registry = new HashMap<>();
        registry.put("TARGET_DB", targetDs);
        // registry.put("ORACLE_RISK_DB", riskDataSource());  // future extension
        return registry;
    }
}
