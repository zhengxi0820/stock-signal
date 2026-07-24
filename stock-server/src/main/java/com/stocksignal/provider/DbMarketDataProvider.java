package com.stocksignal.provider;

import com.stocksignal.data.entity.DailyQuote;
import com.stocksignal.data.mapper.DailyQuoteMapper;
import com.stocksignal.data.provider.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * MarketDataProvider 的 V1 实现：读取 fetch/ Python 脚本灌入 MySQL 的数据。
 */
@Component
public class DbMarketDataProvider implements MarketDataProvider {

    private final DailyQuoteMapper dailyQuoteMapper;

    public DbMarketDataProvider(DailyQuoteMapper dailyQuoteMapper) {
        this.dailyQuoteMapper = dailyQuoteMapper;
    }

    @Override
    public List<DailyQuote> getDailyQuotes(String market, String code, LocalDate start, LocalDate end) {
        return dailyQuoteMapper.findRange(market, code, start, end);
    }
}
