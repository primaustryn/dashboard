package com.shb.dashboard.exception;

/**
 * Thrown when a widget's SQL fails the pre-flight dry-run validation.
 *
 * Distinguishing this from a generic IllegalArgumentException lets the
 * GlobalExceptionHandler return 422 Unprocessable Entity (semantically
 * correct: the request was well-formed YAML but the embedded SQL was invalid)
 * rather than 400 Bad Request (reserved for structural/parsing failures).
 *
 * The {@code dbError} field carries the raw database error message and is
 * included in the HTTP response because this endpoint is admin-only — the
 * deployer needs the DB's exact feedback to diagnose and fix the SQL.
 */
public class SqlDryRunException extends RuntimeException {

    private final String widgetId;
    private final String dbError;

    /**
     * Creates the exception wrapping the underlying DB exception.
     * Used when the pre-flight failure originates from a JDBC {@link java.sql.SQLException}.
     */
    public SqlDryRunException(String widgetId, String dbError, Throwable cause) {
        super("SQL pre-flight failed for widget [" + widgetId + "]: " + dbError, cause);
        this.widgetId = widgetId;
        this.dbError  = dbError;
    }

    /**
     * No-cause variant used when the rejection originates within the engine itself
     * (e.g. query cost exceeds the configured threshold) rather than from a DB exception.
     */
    public SqlDryRunException(String widgetId, String dbError) {
        super("SQL pre-flight failed for widget [" + widgetId + "]: " + dbError);
        this.widgetId = widgetId;
        this.dbError  = dbError;
    }

    /** Returns the widgetId whose SQL failed the pre-flight check. */
    public String getWidgetId() { return widgetId; }

    /** Returns the raw database error message (or engine rejection reason) that caused the failure. */
    public String getDbError()  { return dbError; }
}
