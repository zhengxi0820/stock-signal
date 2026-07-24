package com.stocksignal.strategy;

import java.util.List;
import java.util.Map;

/**
 * 策略配置（"策略类型 + 参数"模型的绑定对象）。
 * 对应 config/strategies.yaml 的 strategies 列表项。
 */
public record StrategyDefinition(
        String name,
        String type,
        String pool,
        Map<String, Object> params,
        List<String> filters
) {
    public int intParam(String key, int defaultValue) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.intValue() : defaultValue;
    }

    public double doubleParam(String key, double defaultValue) {
        Object v = params == null ? null : params.get(key);
        return v instanceof Number n ? n.doubleValue() : defaultValue;
    }
}
