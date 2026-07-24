package com.stocksignal.strategy;

import com.stocksignal.engine.AboveMaFilter;
import com.stocksignal.engine.KdjCrossStrategy;
import com.stocksignal.engine.SignalFilter;
import com.stocksignal.engine.Strategy;
import com.stocksignal.engine.VolumeSurgeFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * 由策略配置构建引擎策略实例。新增策略类型 = 在此注册一个分支（不做通用表达式语言）。
 */
public final class StrategyFactory {

    private StrategyFactory() {
    }

    public static Strategy build(StrategyDefinition def) {
        List<SignalFilter> filters = new ArrayList<>();
        for (String f : def.filters() == null ? List.<String>of() : def.filters()) {
            filters.add(buildFilter(f));
        }
        return switch (def.type()) {
            case "kdj_cross" -> new KdjCrossStrategy(
                    def.intParam("period", 9),
                    def.intParam("kSmooth", 3),
                    def.intParam("dSmooth", 3),
                    def.doubleParam("lowThreshold", 20),
                    def.doubleParam("highThreshold", 80),
                    filters);
            default -> throw new IllegalArgumentException("未知策略类型: " + def.type());
        };
    }

    private static SignalFilter buildFilter(String name) {
        if ("volume_surge".equals(name)) {
            return new VolumeSurgeFilter();
        }
        if (name.startsWith("above_ma")) {
            return new AboveMaFilter(Integer.parseInt(name.substring("above_ma".length())));
        }
        throw new IllegalArgumentException("未知过滤器: " + name);
    }
}
