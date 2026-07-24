package com.stocksignal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证过滤器：AUTH_TOKEN 配置后生效。
 * 放行：登录接口、健康检查、静态页面资源；其余 /api/** 必须携带有效会话 Cookie。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!authService.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.equals("/api/health")
                || path.startsWith("/api/auth/")
                || path.startsWith("/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AuthService.COOKIE_NAME.equals(cookie.getName()) && authService.checkSession(cookie.getValue())) {
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
