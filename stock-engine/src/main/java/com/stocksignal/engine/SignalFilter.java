package com.stocksignal.engine;

import java.util.List;

/**
 * 命名过滤器：对策略产生的信号做二次筛选（如"站上20日线""放量"）。
 * 过滤器是引擎内注册的命名单条件，可组合挂在策略上，但不构成通用表达式语言。
 */
public interface SignalFilter {

    /** 过滤器名，用于配置引用与 strategy_version 生成，如 "above_ma20"。 */
    String name();

    /**
     * 判断 index 处（含当日）是否满足过滤条件。
     *
     * @param candles 完整 K 线序列（升序）
     * @param index   信号所在索引
     * @return 窗口数据不足时一律返回 false（保守，不放行）
     */
    boolean accept(List<Candle> candles, int index);
}
