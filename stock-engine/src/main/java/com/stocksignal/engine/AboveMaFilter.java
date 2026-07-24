package com.stocksignal.engine;

import java.util.List;

/**
 * 站上均线过滤器：当日收盘价 > period 日简单移动平均（默认 20 日线）。
 */
public class AboveMaFilter implements SignalFilter {

    private final int period;

    public AboveMaFilter() {
        this(20);
    }

    public AboveMaFilter(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("均线周期必须为正整数");
        }
        this.period = period;
    }

    @Override
    public String name() {
        return "above_ma" + period;
    }

    @Override
    public boolean accept(List<Candle> candles, int index) {
        if (index < period - 1 || index >= candles.size()) {
            return false;
        }
        double sum = 0;
        for (int i = index - period + 1; i <= index; i++) {
            sum += candles.get(i).close();
        }
        return candles.get(index).close() > sum / period;
    }
}
