package com.shb.dashboard.model;

/**
 * Response body returned by POST /api/v1/admin/widgets/deploy.
 *
 * {@code sqlChunks} and {@code uiSchemaChunks} report how many VARCHAR(4000)
 * rows were written to WIDGET_PAYLOAD — useful for operators to verify that
 * chunking behaved as expected for large SQL or complex uiSchema payloads.
 */
public record DeployResult(
        String widgetId,
        String status,       // "DEPLOYED"
        int    sqlChunks,
        int    uiSchemaChunks
) {}
