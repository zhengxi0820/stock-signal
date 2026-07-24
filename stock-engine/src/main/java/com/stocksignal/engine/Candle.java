package com.stocksignal.engine;

import java.time.LocalDate;

/**
 * 一根 K 线（日线）。引擎的唯一输入单位，与数据源、调度完全解耦。
 */
public record Candle(
        LocalDate date,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
}
