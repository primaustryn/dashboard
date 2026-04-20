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
