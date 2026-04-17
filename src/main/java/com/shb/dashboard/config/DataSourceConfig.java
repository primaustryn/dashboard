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

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.meta")
    public DataSourceProperties metaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("metaDataSource")
    @Primary
    public DataSource metaDataSource(
            @Qualifier("metaDataSourceProperties") DataSourceProperties props) {
        // initializeDataSourceBuilder() translates props.url -> HikariCP jdbcUrl, etc.
        return props.initializeDataSourceBuilder().build();
    }

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

    @Bean
    @ConfigurationProperties("spring.datasource.target")
    public DataSourceProperties targetDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("targetDataSource")
    public DataSource targetDataSource(
            @Qualifier("targetDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }

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

    @Bean
    public Map<String, DataSource> dataSourceRegistry(
            @Qualifier("targetDataSource") DataSource targetDs) {
        Map<String, DataSource> registry = new HashMap<>();
        registry.put("TARGET_DB", targetDs);
        // registry.put("ORACLE_RISK_DB", riskDataSource());  // future extension
        return registry;
    }
}
