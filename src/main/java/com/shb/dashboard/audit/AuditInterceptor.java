package com.shb.dashboard.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;

@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final JdbcTemplate metaJdbc;

    public AuditInterceptor(@Qualifier("metaDataSource") DataSource metaDataSource) {
        this.metaJdbc = new JdbcTemplate(metaDataSource);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String ip     = resolveClientIp(request);
        int    status = response.getStatus();

        // Structured log for log-aggregation pipelines (ELK, Splunk, etc.)
        log.info("AUDIT method={} uri={} ip={} status={}", method, uri, ip, status);

        try {
            metaJdbc.update(
                "INSERT INTO AUDIT_LOG (http_method, request_uri, client_ip, status_code) VALUES (?,?,?,?)",
                method, uri, ip, status);
        } catch (Exception dbEx) {
            // Never let audit failure break the main request flow.
            log.error("Failed to write audit record", dbEx);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Respect reverse-proxy headers (nginx, L4/L7 LB in financial networks).
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
