package com.stocksignal.data.provider;

import com.stocksignal.data.entity.DailyQuote;

import java.time.LocalDate;
import java.util.List;

/**
 * 行情数据获取抽象。V1 唯一实现是从 MySQL 读取 fetch/ Python 脚本灌入的数据；
 * 未来可平替为纯 Java 抓取或付费数据源，替换实现不影响消费方。
 */
public interface MarketDataProvider {

    /**
     * 按交易日升序返回某股票 [start, end] 区间的日线。
     */
    List<DailyQuote> getDailyQuotes(String market, String code, LocalDate start, LocalDate end);
}
