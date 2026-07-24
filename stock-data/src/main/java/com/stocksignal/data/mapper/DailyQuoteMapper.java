package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.DailyQuote;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * daily_quote 表访问接口。业务代码只依赖本接口，SQL 收敛在 Mapper XML。
 */
public interface DailyQuoteMapper {

    /**
     * 按交易日升序查询某股票 [start, end] 区间的日线。
     */
    List<DailyQuote> findRange(@Param("market") String market,
                               @Param("code") String code,
                               @Param("start") LocalDate start,
                               @Param("end") LocalDate end);

    /**
     * 查询某股票已入库的最新交易日；无数据返回 null。用于增量抓取起点判断。
     */
    LocalDate findLatestTradeDate(@Param("market") String market,
                                  @Param("code") String code);

    /**
     * 每个市场的最新交易日（数据新鲜度展示）。
     */
    List<Map<String, Object>> findLatestTradeDatePerMarket();
}
