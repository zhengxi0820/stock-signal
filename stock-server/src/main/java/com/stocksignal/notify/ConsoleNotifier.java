package com.stocksignal.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 控制台通知实现（默认）。--stock.notifier=wechat 时由 WechatNotifier 替换。
 */
@Component
@ConditionalOnProperty(name = "stock.notifier", havingValue = "console", matchIfMissing = true)
public class ConsoleNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNotifier.class);

    @Override
    public void send(String title, String content) {
        log.info("=== 通知 ===\n{}\n{}\n============", title, content);
    }
}
