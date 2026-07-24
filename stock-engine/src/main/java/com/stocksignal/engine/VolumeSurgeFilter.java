package com.stocksignal.engine;

import java.util.List;

/**
 * 放量过滤器：当日成交量 > 前 period 日（不含当日）均量 × ratio。
 * 默认：前 5 日均量的 1.5 倍。
 */
public class VolumeSurgeFilter implements SignalFilter {

    private final int period;
    private final double ratio;

    public VolumeSurgeFilter() {
        this(5, 1.5);
    }

    public VolumeSurgeFilter(int period, double ratio) {
        if (period < 1 || ratio <= 0) {
            throw new IllegalArgumentException("参数非法");
        }
        this.period = period;
        this.ratio = ratio;
    }

    @Override
    public String name() {
        return "volume_surge";
    }

    @Override
    public boolean accept(List<Candle> candles, int index) {
        if (index < period || index >= candles.size()) {
            return false;
        }
        double sum = 0;
        for (int i = index - period; i < index; i++) {
            sum += candles.get(i).volume();
        }
        return candles.get(index).volume() > sum / period * ratio;
    }
}
