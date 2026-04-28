package com.shb.dashboard.model;

/**
 * Immutable snapshot of a widget's resolved metadata.
 *
 * Declared as a Java record so Jackson (≥ 2.12, bundled with Spring Boot 3.x)
 * can serialize and deserialize it through Redis without any additional
 * annotations.  Jackson maps record components by their canonical accessor
 * names (widgetId(), targetDb(), …) and uses the canonical constructor for
 * deserialization — exactly what GenericJackson2JsonRedisSerializer requires.
 *
 * Backward-compatibility: all call sites that construct via
 *   {@code new WidgetMeta(widgetId, targetDb, querySql, dynamicConfig)}
 * and access via {@code .widgetId()}, {@code .targetDb()}, etc. require
 * zero changes.
 */
public record WidgetMeta(
        String widgetId,
        String targetDb,
        String querySql,
        String dynamicConfig   // raw JSON string; parsed to JsonNode in the service layer
) {}
