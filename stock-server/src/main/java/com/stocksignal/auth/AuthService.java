package com.stocksignal.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最小认证（单账号）。公网部署的强制关卡，见 docs/architecture.md「安全边界」。
 *
 * <p>口令走环境变量 AUTH_TOKEN；未配置时认证关闭（仅限本机开发）。
 * 启用后：POST /api/auth/login 用口令换 HttpOnly Cookie（值为口令的派生哈希，不存明文）。
 *
 * <p>防爆破：同一 IP 连续失败 {@value #MAX_FAILURES} 次锁定 {@value #LOCK_MINUTES} 分钟，
 * 成功/失败均写审计日志（格式对齐 fail2ban：`AUTH login fail ip=...`）。
 */
@Component
public class AuthService {

    public static final String COOKIE_NAME = "stock_session";
    static final int MAX_FAILURES = 5;
    static final int LOCK_MINUTES = 15;

    private static final Logger log = LoggerFactory.getLogger("AUTH");

    private final String token;
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public AuthService(@Value("${AUTH_TOKEN:}") String token) {
        this.token = token;
    }

    /** 认证是否启用（未配置 AUTH_TOKEN 时关闭，仅限本机开发）。 */
    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    /** 该 IP 当前是否处于锁定期。 */
    public boolean isLocked(String ip) {
        Instant until = lockedUntil.get(ip);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            lockedUntil.remove(ip);
            failures.remove(ip);
            return false;
        }
        return true;
    }

    /**
     * 校验口令并记录审计日志。调用前必须先查 isLocked。
     *
     * @return true 口令正确
     */
    public boolean checkPassword(String password, String ip) {
        if (!enabled()) {
            return true;
        }
        if (token.equals(password)) {
            failures.remove(ip);
            log.info("AUTH login success ip={}", ip);
            return true;
        }
        int count = failures.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
        log.warn("AUTH login fail ip={} attempt={}", ip, count);
        if (count >= MAX_FAILURES) {
            lockedUntil.put(ip, Instant.now().plusSeconds(LOCK_MINUTES * 60L));
            log.warn("AUTH locked ip={} for {}min after {} failures", ip, LOCK_MINUTES, count);
        }
        return false;
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
