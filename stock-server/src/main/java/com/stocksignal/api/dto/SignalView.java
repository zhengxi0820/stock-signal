package com.stocksignal.api.dto;

import com.stocksignal.api.EastmoneyLink;
import com.stocksignal.data.entity.Signal;

import java.time.LocalDate;
import java.util.Map;

/**
 * 信号视图（API 出参）：signals 表记录 + 解析后的指标快照 + 东财跳转链接。
 */
public record SignalView(
        String market,
        String code,
        LocalDate tradeDate,
        String strategy,
        String signalType,
        Map<String, Object> indicator,
        String eastmoneyUrl
) {
    @SuppressWarnings("unchecked")
    public static SignalView from(Signal s, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        Map<String, Object> indicator;
        try {
            indicator = objectMapper.readValue(s.getIndicatorSnapshot(), Map.class);
        } catch (Exception e) {
            indicator = Map.of();
        }
        return new SignalView(s.getMarket(), s.getCode(), s.getTradeDate(), s.getStrategy(),
                s.getSignalType(), indicator, EastmoneyLink.quoteUrl(s.getMarket(), s.getCode()));
    }
}
