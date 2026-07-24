package com.stocksignal.api;

import com.stocksignal.data.provider.MarketDataProvider;
import com.stocksignal.engine.Candle;
import com.stocksignal.engine.KdjCalculator;
import com.stocksignal.engine.KdjPoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 指标序列（按需现算，不落库）：用于「基础指标图」。目前支持 KDJ。
 */
@Tag(name = "indicators", description = "指标序列（现算）")
@RestController
@RequestMapping("/api/stocks/{market}/{code}/indicators")
public class IndicatorController {

    private final MarketDataProvider marketDataProvider;

    public IndicatorController(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public record IndicatorPoint(LocalDate date, Double close, Double k, Double d, Double j) {
    }

    @Operation(summary = "KDJ 指标序列",
            description = "返回区间内收盘价 + K/D/J 序列（窗口不足处 K/D/J 为 null）。指标不落库，实时计算。")
    @GetMapping("/kdj")
    public List<IndicatorPoint> kdj(
            @Parameter(description = "市场", example = "SH") @PathVariable String market,
            @Parameter(description = "代码", example = "600519") @PathVariable String code,
            @Parameter(description = "开始日期，缺省 6 个月前") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "结束日期，缺省今天") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate e = end == null ? LocalDate.now() : end;
        // KDJ 需要前置窗口预热，向前多取 1 年，输出时裁掉
        LocalDate fetchStart = (start == null ? e.minusMonths(6) : start).minusYears(1);
        LocalDate displayStart = start == null ? e.minusMonths(6) : start;

        List<Candle> candles = marketDataProvider.getDailyQuotes(market, code, fetchStart, e)
                .stream()
                .map(q -> new Candle(q.getTradeDate(),
                        q.getOpen().doubleValue(), q.getHigh().doubleValue(),
                        q.getLow().doubleValue(), q.getClose().doubleValue(), q.getVolume()))
                .toList();
        List<KdjPoint> kdj = new KdjCalculator().calculate(candles);

        List<IndicatorPoint> result = new java.util.ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).date().isBefore(displayStart)) {
                continue;
            }
            KdjPoint p = kdj.get(i);
            result.add(new IndicatorPoint(candles.get(i).date(), candles.get(i).close(),
                    p == null ? null : p.k(), p == null ? null : p.d(), p == null ? null : p.j()));
        }
        return result;
    }
}
