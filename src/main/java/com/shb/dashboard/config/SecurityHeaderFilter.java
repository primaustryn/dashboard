package com.shb.dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityHeaderFilter extends OncePerRequestFilter {

    /**
     * Attaches security-hardening response headers to every HTTP response and
     * disables browser caching for all {@code /api/*} paths.
     *
     * <ul>
     *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME-type sniffing
     *       (e.g. a script disguised as an image).</li>
     *   <li>{@code X-Frame-Options: DENY} — blocks iframe embedding to prevent clickjacking.</li>
     *   <li>{@code Content-Security-Policy} — restricts resource loading to the same origin;
     *       inline scripts and styles are allowed to support ECharts.</li>
     *   <li>{@code Referrer-Policy: no-referrer} — suppresses the Referer header on
     *       cross-origin requests.</li>
     *   <li>{@code Cache-Control: no-store} on API paths — financial data must never be
     *       persisted in browser or intermediate caches.</li>
     * </ul>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Prevent MIME-type sniffing (e.g. script disguised as image).
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Block rendering inside iframes — prevents clickjacking.
        response.setHeader("X-Frame-Options", "DENY");

        // Restrict resource loading to same origin; inline scripts/styles blocked.
        // Adjust script-src if ECharts bundles require 'unsafe-eval'.
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");

        // Do not send Referer header to third-party origins.
        response.setHeader("Referrer-Policy", "no-referrer");

        // Prevent browser from caching API responses — financial data must not be stored.
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }

        filterChain.doFilter(request, response);
    }
}
