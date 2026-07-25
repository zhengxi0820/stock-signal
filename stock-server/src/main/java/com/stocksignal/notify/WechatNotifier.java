package com.stocksignal.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 微信通知实现（PushPlus webhook，消息推送到微信服务号）。
 *
 * <p>启用方式：--stock.notifier=wechat，token 走环境变量 PUSHPLUS_TOKEN（严禁入仓）。
 * token 未配置时不发消息、只记错误日志，不影响主流程。
 */
@Component
@ConditionalOnProperty(name = "stock.notifier", havingValue = "wechat")
public class WechatNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(WechatNotifier.class);
    private static final String PUSH_URL = "https://www.pushplus.plus/send";

    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatNotifier(@Value("${PUSHPLUS_TOKEN:}") String token) {
        this.token = token;
    }

    @Override
    public void send(String title, String content) {
        if (token == null || token.isBlank()) {
            log.error("PUSHPLUS_TOKEN 未配置，跳过微信推送: {}", title);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("token", token, "title", title, "content", content, "template", "txt"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(PUSH_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // PushPlus 业务错误也返回 HTTP 200，必须检查响应体 code 字段（code=200 才为成功）
            if (response.statusCode() != 200) {
                log.error("PushPlus 推送失败 HTTP {}: {}", response.statusCode(), response.body());
                return;
            }
            String respBody = response.body();
            if (respBody != null && respBody.contains("\"code\":200")) {
                log.info("微信推送成功: {}", title);
            } else {
                log.error("PushPlus 推送被拒: {}", respBody);
            }
        } catch (Exception e) {
            log.error("PushPlus 推送异常: {}", e.getMessage());
        }
    }
}
