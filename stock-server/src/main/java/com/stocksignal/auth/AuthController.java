package com.stocksignal.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 登录/登出（最小认证，单账号）。
 */
@Tag(name = "auth", description = "认证（单账号）")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "登录", description = "口令正确则种下 HttpOnly 会话 Cookie；认证未启用时直接成功")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        if (!authService.enabled()) {
            return Map.of("auth", "disabled");
        }
        if (!authService.checkPassword(body.get("password"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "口令错误");
        }
        Cookie cookie = new Cookie(AuthService.COOKIE_NAME, authService.sessionValue());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 3600);
        response.addCookie(cookie);
        return Map.of("auth", "ok");
    }

    @Operation(summary = "登出", description = "清除会话 Cookie")
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthService.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return Map.of("auth", "bye");
    }
}
