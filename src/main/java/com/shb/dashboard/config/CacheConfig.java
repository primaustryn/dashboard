package com.shb.dashboard.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Distributed cache configuration — replaces the local Caffeine cache so that
 * all nodes in the cluster share a single, consistent cache namespace.
 *
 * <h3>Cache taxonomy</h3>
 * <pre>
 *  ┌──────────────────────┬──────────┬───────────────────────────────────────────┐
 *  │ Cache name           │ TTL      │ What is stored                            │
 *  ├──────────────────────┼──────────┼───────────────────────────────────────────┤
 *  │ widgetMetadataCache  │ 24 hours │ WidgetMeta (SQL + uiSchema JSON decoded    │
 *  │                      │          │ from WIDGET_PAYLOAD).  Changes only on     │
 *  │                      │          │ GitOps deploy; long TTL is safe.           │
 *  ├──────────────────────┼──────────┼───────────────────────────────────────────┤
 *  │ widgetDataCache      │ 5 minutes│ List<Map<String,Object>> rows returned by  │
 *  │                      │          │ the Target DB query.  Protects the DB from │
 *  │                      │          │ redundant hits on every page load.          │
 *  └──────────────────────┴──────────┴───────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Serialization</h3>
 * Keys are stored as plain UTF-8 strings for human readability in Redis CLI.
 * Values are stored as polymorphic JSON via {@link GenericJackson2JsonRedisSerializer},
 * which embeds a {@code @class} type hint so multi-node deserialisation is safe
 * even when class names change (Spring Boot handles null-safe upgrades).
 *
 * <h3>Transaction awareness</h3>
 * {@link RedisCacheManager.RedisCacheManagerBuilder#transactionAware()} is
 * intentionally <em>NOT</em> called here.  Transaction-sync eviction is managed
 * manually via {@code TransactionSynchronizationManager.registerSynchronization}
 * in {@code WidgetDeployService} so that cache eviction only fires after the
 * meta-DB commit is durable — not at Spring's internal transaction close phase,
 * which can precede the actual I/O flush on some JDBC drivers.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Caches assembled {@code WidgetMeta} (SQL + uiSchema) from WIDGET_PAYLOAD. */
    public static final String METADATA_CACHE = "widgetMetadataCache";

    /**
     * Short-lived cache for Target DB query results.
     * Evicted by {@code WidgetDeployService} on re-deploy and by TTL expiry.
     */
    public static final String DATA_CACHE = "widgetDataCache";

    /**
     * Builds the distributed {@link RedisCacheManager} with per-cache TTL overrides.
     *
     * <p>Serialization: keys are stored as plain UTF-8 strings for human readability in Redis CLI
     * (e.g. {@code widgetMetadataCache::WD_SALES_REGION}).  Values are stored as polymorphic
     * JSON via {@link GenericJackson2JsonRedisSerializer}, which embeds a {@code @class} type
     * hint enabling safe cross-node deserialization even when object structure evolves.
     *
     * <p>TTL strategy: the default is 30 minutes; per-cache overrides narrow this to
     * 24 hours for metadata (changes only on deploy) and 5 minutes for query results
     * (short enough to reflect Target DB changes without hammering the DB on every request).
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Base config shared by all caches unless overridden below.
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                // Human-readable Redis keys: "widgetMetadataCache::WD_SALES_REGION"
                .serializeKeysWith(
                        SerializationPair.fromSerializer(new StringRedisSerializer()))
                // Type-aware JSON values — safe for cross-node deserialization.
                .serializeValuesWith(
                        SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> perCacheConfigs = Map.of(
                METADATA_CACHE, base.entryTtl(Duration.ofHours(24)),
                DATA_CACHE,     base.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(30)))
                .withInitialCacheConfigurations(perCacheConfigs)
                // transactionAware() is deliberately omitted — see class-level Javadoc.
                .build();
    }
}
