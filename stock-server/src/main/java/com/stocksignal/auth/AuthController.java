package com.stocksignal.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

/**
 * 登录/登出（最小认证，单账号）。
 * 防爆破：同一 IP 连续失败 5 次锁定 15 分钟，锁定期返回 429。
 * Cookie：HttpOnly + SameSite=Strict + Secure（HTTPS 部署时；本地 HTTP 开发可用 SECURE_COOKIE=false 关闭）。
 */
@Tag(name = "auth", description = "认证（单账号）")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          @Value("${SECURE_COOKIE:true}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @Operation(summary = "登录", description = "口令正确则种下 HttpOnly 会话 Cookie；连续失败 5 次锁定 15 分钟（429）")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        if (!authService.enabled()) {
            return Map.of("auth", "disabled");
        }
        String ip = clientIp(request);
        if (authService.isLocked(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "失败次数过多，已锁定 15 分钟");
        }
        if (!authService.checkPassword(body.get("password"), ip)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "口令错误");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthService.COOKIE_NAME, authService.sessionValue())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build().toString());
        return Map.of("auth", "ok");
    }

    @Operation(summary = "登出", description = "清除会话 Cookie")
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthService.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build().toString());
        return Map.of("auth", "bye");
    }

    /** 反向代理（Caddy）后取真实客户端 IP。 */
    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
