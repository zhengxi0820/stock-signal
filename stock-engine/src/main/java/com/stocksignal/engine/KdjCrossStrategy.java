package com.stocksignal.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * KDJ 交叉策略（配置类型 "kdj_cross"）。
 *
 * <p>语义以 docs/architecture.md「KDJ 计算语义（冻结）」为准：
 * <ul>
 *   <li>金叉：昨日 K≤D 且今日 K>D，且交叉日 K 值 ≤ 低位阈值（默认 20）</li>
 *   <li>死叉：昨日 K≥D 且今日 K<D，且交叉日 K 值 ≥ 高位阈值（默认 80）</li>
 *   <li>窗口不足（KDJ 序列为 null 的位置）不出信号</li>
 * </ul>
 * 触发信号再经过全部命名过滤器放行后才产出。
 */
public class KdjCrossStrategy implements Strategy {

    private final KdjCalculator calculator;
    private final double lowThreshold;
    private final double highThreshold;
    private final List<SignalFilter> filters;

    /** 默认参数：9,3,3，低位 20，高位 80，无过滤器。 */
    public KdjCrossStrategy() {
        this(9, 3, 3, 20, 80, List.of());
    }

    public KdjCrossStrategy(int period, int kSmooth, int dSmooth,
                            double lowThreshold, double highThreshold,
                            List<SignalFilter> filters) {
        this.calculator = new KdjCalculator(period, kSmooth, dSmooth);
        this.lowThreshold = lowThreshold;
        this.highThreshold = highThreshold;
        this.filters = List.copyOf(filters);
    }

    @Override
    public String name() {
        return "kdj_cross";
    }

    @Override
    public String version() {
        StringBuilder sb = new StringBuilder(name())
                .append(":th=").append(lowThreshold).append('/').append(highThreshold);
        if (!filters.isEmpty()) {
            sb.append(":f=");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(filters.get(i).name());
            }
        }
        return sb.toString();
    }

    @Override
    public List<EngineSignal> evaluate(List<Candle> candles) {
        List<KdjPoint> kdj = calculator.calculate(candles);
        List<EngineSignal> signals = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            KdjPoint prev = kdj.get(i - 1);
            KdjPoint curr = kdj.get(i);
            if (prev == null || curr == null) {
                continue;
            }
            SignalType type = null;
            if (prev.k() <= prev.d() && curr.k() > curr.d() && curr.k() <= lowThreshold) {
                type = SignalType.GOLDEN_CROSS;
            } else if (prev.k() >= prev.d() && curr.k() < curr.d() && curr.k() >= highThreshold) {
                type = SignalType.DEATH_CROSS;
            }
            if (type == null) {
                continue;
            }
            EngineSignal signal = new EngineSignal(candles.get(i).date(), type, curr.k(), curr.d(), curr.j());
            boolean accepted = true;
            for (SignalFilter filter : filters) {
                if (!filter.accept(candles, i)) {
                    accepted = false;
                    break;
                }
            }
            if (accepted) {
                signals.add(signal);
            }
        }
        return signals;
    }
}
