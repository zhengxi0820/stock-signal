package com.stocksignal.engine;

import java.time.LocalDate;

/**
 * 引擎产出的信号：某交易日触发，附触发时 KDJ 快照。
 */
public record EngineSignal(
        LocalDate tradeDate,
        SignalType type,
        double k,
        double d,
        double j
) {
}
