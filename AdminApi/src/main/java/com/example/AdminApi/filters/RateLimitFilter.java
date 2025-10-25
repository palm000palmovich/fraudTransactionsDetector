package com.example.AdminApi.filters;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter using Bucket4j.
 *
 * Limits requests per IP address:
 * - Public API (/api/transactions): 10000 requests per minute (LOAD TESTING MODE)
 * - Admin API (/api/rules, /api/admin): 30 requests per minute
 * - Stats API (/api/stats): 60 requests per minute
 */
@Slf4j
@Component
public class RateLimitFilter implements Filter {

    // Cache of buckets per IP address
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIP(httpRequest);
        String path = httpRequest.getRequestURI();

        // Skip rate limiting for actuator endpoints (Prometheus, health checks)
        if (path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        // Determine rate limit based on path
        int requestsPerMinute = getRateLimitForPath(path);

        // Get or create bucket for this IP
        Bucket bucket = cache.computeIfAbsent(clientIp, k -> createBucket(requestsPerMinute));

        // Try to consume 1 token
        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            long availableTokens = bucket.getAvailableTokens();
            httpResponse.setHeader("X-Rate-Limit-Limit", String.valueOf(requestsPerMinute));
            httpResponse.setHeader("X-Rate-Limit-Remaining", String.valueOf(availableTokens));

            chain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);

            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("X-Rate-Limit-Limit", String.valueOf(requestsPerMinute));
            httpResponse.setHeader("X-Rate-Limit-Remaining", "0");
            httpResponse.setHeader("Retry-After", "60"); // Retry after 60 seconds

            String errorResponse = String.format(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Maximum %d requests per minute allowed.\",\"retryAfter\":60}",
                requestsPerMinute
            );
            httpResponse.getWriter().write(errorResponse);
        }
    }

    /**
     * Create a bucket with specified capacity per minute
     */
    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute,
            Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Determine rate limit based on API path
     */
    private int getRateLimitForPath(String path) {
        if (path.startsWith("/api/transactions")) {
            return 10000; // Public transaction API: 10000 req/min (LOAD TESTING MODE)
        } else if (path.startsWith("/api/rules") || path.startsWith("/api/admin")) {
            return 30; // Admin API: 30 req/min
        } else if (path.startsWith("/api/stats")) {
            return 60; // Stats API: 60 req/min
        }
        return 100; // Default: 100 req/min
    }

    /**
     * Extract client IP address from request, considering proxy headers
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP if multiple proxies
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        return request.getRemoteAddr();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RateLimitFilter initialized");
    }

    @Override
    public void destroy() {
        cache.clear();
        log.info("RateLimitFilter destroyed");
    }
}
