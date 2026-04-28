package com.shb.dashboard.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * SQL pre-flight dry-run failure — 422 Unprocessable Entity.
     *
     * 422 is semantically correct: the YAML was structurally valid (not 400)
     * but the embedded SQL failed business validation against the target DB.
     *
     * The raw DB error IS included in the response because this is an admin-only
     * endpoint — the deployer needs the exact DB feedback to diagnose and fix
     * the SQL.  Contrast with handleDataAccess() below, which serves the public
     * widget endpoint and must never leak schema details to untrusted callers.
     */
    @ExceptionHandler(SqlDryRunException.class)
    public ProblemDetail handleSqlDryRun(SqlDryRunException ex) {
        log.warn("SQL pre-flight failed for widget [{}]: {}", ex.getWidgetId(), ex.getDbError());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "SQL pre-flight validation failed: " + ex.getDbError());
        pd.setProperty("widgetId", ex.getWidgetId());
        return pd;
    }

    /**
     * Translates {@link WidgetNotFoundException} to 404 Not Found.
     * The exception message includes the widgetId, which is safe to surface to the caller.
     */
    @ExceptionHandler(WidgetNotFoundException.class)
    public ProblemDetail handleWidgetNotFound(WidgetNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Translates structural validation failures to 400 Bad Request.
     * Covers missing required fields, unknown targetDb keys, invalid widgetId format,
     * and other caller-correctable request errors.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Translates Spring {@link DataAccessException} to 500 Internal Server Error,
     * suppressing the raw SQL detail and schema information from the response body.
     * Raw DB errors must never reach untrusted callers as they reveal table names,
     * column names, and DB product details useful for SQL injection reconnaissance.
     */
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException ex) {
        log.error("Database error", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Please contact the administrator.");
    }

    /**
     * Catch-all handler for any exception not matched by a more specific handler above.
     * Returns 500 with a generic message to avoid leaking internal implementation details.
     * The full stack trace is logged at ERROR level for operator investigation.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact the administrator.");
    }
}
