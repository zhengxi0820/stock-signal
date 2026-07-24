package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.Signal;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * signals 表访问接口。插入用 INSERT IGNORE 保证重跑幂等（唯一键冲突即跳过）。
 */
public interface SignalMapper {

    /**
     * 幂等插入：唯一键 (market, code, trade_date, strategy, strategy_version, signal_type)
     * 冲突时忽略，返回实际插入行数（0 或 1）。
     */
    int insertIgnore(Signal signal);

    List<Signal> findByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    List<Signal> findByStock(@Param("market") String market,
                             @Param("code") String code,
                             @Param("start") LocalDate start,
                             @Param("end") LocalDate end);

    /**
     * 每只股票每个策略取最近一次信号（用于"当前状态"大屏：
     * 最近信号为金叉 → 当前金叉状态；为死叉 → 当前死叉状态）。
     */
    List<Signal> findLatestPerStock();
}
