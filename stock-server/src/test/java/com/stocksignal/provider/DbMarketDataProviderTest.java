package com.stocksignal.provider;

import com.stocksignal.data.entity.DailyQuote;
import com.stocksignal.data.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MarketDataProvider 的集成验证（M1a 验收）：读取库中 600519 的真实数据。
 * 本机 MySQL 不可达时自动跳过，不影响无数据库环境的构建。
 */
@SpringBootTest
class DbMarketDataProviderTest {

    @Autowired
    private MarketDataProvider provider;

    private static boolean mysqlReachable() {
        try (var conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/stock_signal", "stock", "stock")) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void readsRealDataFor600519() {
        assumeTrue(mysqlReachable(), "本机 MySQL 不可达，跳过集成验证");

        List<DailyQuote> quotes = provider.getDailyQuotes(
                "SH", "600519", LocalDate.of(2023, 7, 1), LocalDate.now());

        assertFalse(quotes.isEmpty());
        // 升序且业务键无重复
        for (int i = 1; i < quotes.size(); i++) {
            assertTrue(quotes.get(i).getTradeDate().isAfter(quotes.get(i - 1).getTradeDate()));
        }
        assertEquals("qfq", quotes.get(0).getAdjust());
        System.out.printf("[IT] 600519 读取 %d 行，%s ~ %s%n",
                quotes.size(), quotes.get(0).getTradeDate(), quotes.get(quotes.size() - 1).getTradeDate());
    }
}
