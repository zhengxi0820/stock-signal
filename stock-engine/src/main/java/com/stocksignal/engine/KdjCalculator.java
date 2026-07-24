package com.stocksignal.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 中式 KDJ 计算器（东财/同花顺口径），手写实现。
 *
 * <p>不用 TA4J 的 Stochastic：那是西式定义（%D 为 %K 的 N 日简单平均），
 * 与中式 SMA(X,N,1) 递推平滑口径不同，会导致交叉日期与东财对不齐。
 * 语义以 docs/architecture.md「KDJ 计算语义（冻结）」为准，改动需先改该文档。
 *
 * <ul>
 *   <li>RSV = (C - LLV(n)) / (HHV(n) - LLV(n)) × 100；HHV == LLV 时 RSV 取 50（中性）</li>
 *   <li>K = SMA(RSV, m1, 1)，D = SMA(K, m2, 1)，J = 3K - 2D；K/D 初值 50</li>
 *   <li>前 (n-1) 根 K 线窗口不足，对应位置返回 null（不出信号、不外推）</li>
 * </ul>
 */
public class KdjCalculator {

    private final int period;
    private final int kSmooth;
    private final int dSmooth;

    /** 中式 KDJ 默认参数 9,3,3。 */
    public KdjCalculator() {
        this(9, 3, 3);
    }

    public KdjCalculator(int period, int kSmooth, int dSmooth) {
        if (period < 1 || kSmooth < 1 || dSmooth < 1) {
            throw new IllegalArgumentException("KDJ 参数必须为正整数");
        }
        this.period = period;
        this.kSmooth = kSmooth;
        this.dSmooth = dSmooth;
    }

    /**
     * 计算整段 KDJ 序列，与输入 candles 等长、按索引对齐；窗口不足处为 null。
     */
    public List<KdjPoint> calculate(List<Candle> candles) {
        List<KdjPoint> result = new ArrayList<>(candles.size());
        double k = 50.0;
        double d = 50.0;
        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                result.add(null);
                continue;
            }
            double hhv = Double.MIN_VALUE;
            double llv = Double.MAX_VALUE;
            for (int j = i - period + 1; j <= i; j++) {
                hhv = Math.max(hhv, candles.get(j).high());
                llv = Math.min(llv, candles.get(j).low());
            }
            double close = candles.get(i).close();
            double rsv = (hhv == llv) ? 50.0 : (close - llv) / (hhv - llv) * 100.0;
            // SMA(X, N, 1)：X = (1×当日值 + (N-1)×前值) / N
            k = (rsv + (kSmooth - 1) * k) / kSmooth;
            d = (k + (dSmooth - 1) * d) / dSmooth;
            result.add(new KdjPoint(k, d, 3 * k - 2 * d));
        }
        return result;
    }
}
