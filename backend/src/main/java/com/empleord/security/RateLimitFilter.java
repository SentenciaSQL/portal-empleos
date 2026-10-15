package com.empleord.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.empleord.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = limitFor(request.getMethod(), request.getRequestURI());
        if (limit != null && !allow(clientKey(request) + "|" + limit.name(), limit.max(), limit.windowMs())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error("Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.", java.util.List.of()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, int max, long windowMs) {
        Deque<Long> deque = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.removeFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(now);
        }
        if (hits.size() > 20_000) {
            hits.clear();
        }
        return true;
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Limit limitFor(String method, String path) {
        if (!"POST".equals(method)) {
            return null;
        }
        if (path.equals("/api/auth/login")) {
            return new Limit("login", 10, 60_000);
        }
        if (path.equals("/api/auth/register") || path.equals("/api/auth/forgot-password") || path.equals("/api/auth/resend-verification") || path.equals("/api/auth/reset-password")) {
            return new Limit("auth-write", 5, 60_000);
        }
        if (path.startsWith("/api/jobs/") && path.endsWith("/reports")) {
            return new Limit("reports", 8, 3_600_000);
        }
        return null;
    }

    private record Limit(String name, int max, long windowMs) {
    }
}
