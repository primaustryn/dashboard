package com.shb.dashboard.config;

import com.shb.dashboard.audit.AuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    /** Injects the AuditInterceptor so it can be registered on the MVC interceptor chain. */
    public WebConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    /**
     * Allows the Vite dev server (port 5173) to call backend APIs during local development.
     * Standard HTTP verbs and the {@code Content-Type} header are permitted; credentials
     * are not explicitly allowed to avoid session-based CORS complexity.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }

    /**
     * Registers the AuditInterceptor on all {@code /api/**} paths so every API request
     * is captured in the AUDIT_LOG table and structured log output.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}
