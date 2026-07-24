package com.stocksignal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksignal.api.dto.SignalView;
import com.stocksignal.data.mapper.SignalMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 信号查询。
 */
@Tag(name = "signals", description = "策略信号查询")
@RestController
@RequestMapping("/api/signals")
public class SignalQueryController {

    private final SignalMapper signalMapper;
    private final ObjectMapper objectMapper;

    public SignalQueryController(SignalMapper signalMapper, ObjectMapper objectMapper) {
        this.signalMapper = signalMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "按交易日查询信号", description = "date 缺省为今天；用于「今日信号」页")
    @GetMapping
    public List<SignalView> byDate(
            @Parameter(description = "交易日，缺省今天", example = "2026-07-24")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return signalMapper.findByTradeDate(d).stream()
                .map(s -> SignalView.from(s, objectMapper))
                .toList();
    }

    @Operation(summary = "各股票各策略的最新信号（当前状态大屏）",
            description = "每只股票每个策略取最近一次信号；state=GOLDEN_CROSS 过滤出当前处于金叉状态的。前端按股票聚合展示多策略标记。")
    @GetMapping("/current")
    public List<SignalView> current(
            @Parameter(description = "按信号类型过滤", example = "GOLDEN_CROSS")
            @RequestParam(required = false) String state) {
        return signalMapper.findLatestPerStock().stream()
                .filter(s -> state == null || s.getSignalType().equals(state))
                .map(s -> SignalView.from(s, objectMapper))
                .toList();
    }

    @Operation(summary = "按股票查询历史信号", description = "用于「历史信号查询」页")
    @GetMapping("/stock")
    public List<SignalView> byStock(
            @Parameter(description = "市场", example = "SH") @RequestParam String market,
            @Parameter(description = "代码", example = "600519") @RequestParam String code,
            @Parameter(description = "开始日期") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Parameter(description = "结束日期") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        LocalDate e = end == null ? LocalDate.now() : end;
        LocalDate s = start == null ? e.minusYears(3) : start;
        return signalMapper.findByStock(market, code, s, e).stream()
                .map(sig -> SignalView.from(sig, objectMapper))
                .toList();
    }
}
