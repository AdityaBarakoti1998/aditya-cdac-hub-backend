package com.cdac.cdachub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
// This file is a custom filter that implements rate limiting for incoming requests. It uses a ConcurrentHashMap to track the number of requests made by each IP address within a specified time window. If an IP address exceeds the allowed number of requests, it responds with a 429 Too Many Requests status code.
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_PER_HOUR = 10;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        boolean isSubmit = request.getRequestURI().equals("/api/student/projects")
                && request.getMethod().equals("POST");

        if (isSubmit) {
            String key = request.getRemoteAddr();
            long now = System.currentTimeMillis();
            windowStart.putIfAbsent(key, now);

            if (now - windowStart.get(key) > 3600_000) {
                windowStart.put(key, now);
                counts.put(key, new AtomicInteger(0));
            }

            if (counts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet() > MAX_PER_HOUR) {
                response.setStatus(429); // 429 Too Many Requests, NOT 403
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many submissions. Try again later.\"}");
                return; // Stops execution so it doesn't hit a 403 downstream
            }
        }

        filterChain.doFilter(request, response);
    }
}