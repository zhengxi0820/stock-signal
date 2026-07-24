package com.stocksignal.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 最小认证（单账号）。M6 公网暴露前的强制关卡，见 docs/architecture.md「安全边界」。
 *
 * <p>口令走环境变量 AUTH_TOKEN；未配置时认证关闭（仅限本机开发）。
 * 启用后：POST /api/auth/login 用口令换 HttpOnly Cookie（值为口令的派生哈希，不存明文）。
 */
@Component
public class AuthService {

    public static final String COOKIE_NAME = "stock_session";

    private final String token;

    public AuthService(@Value("${AUTH_TOKEN:}") String token) {
        this.token = token;
    }

    /** 认证是否启用（未配置 AUTH_TOKEN 时关闭，仅限本机开发）。 */
    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    public boolean checkPassword(String password) {
        return enabled() && token.equals(password);
    }

    /** 由口令派生的会话值（sha256(token + 固定盐)），Cookie 中不存口令明文。 */
    public String sessionValue() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((token + "::stock-signal-session").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean checkSession(String value) {
        return enabled() && value != null && value.equals(sessionValue());
    }
}
