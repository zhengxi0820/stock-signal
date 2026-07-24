package com.stocksignal.data.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 策略信号。对应 signals 表，幂等唯一键 (market, code, trade_date, strategy, strategy_version, signal_type)。
 */
public class Signal {

    private Long id;
    private String market;
    private String code;
    private LocalDate tradeDate;
    private String strategy;
    private String strategyVersion;
    private String signalType;
    private String indicatorSnapshot;
    private String runId;
    private LocalDateTime generatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public void setStrategyVersion(String strategyVersion) {
        this.strategyVersion = strategyVersion;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getIndicatorSnapshot() {
        return indicatorSnapshot;
    }

    public void setIndicatorSnapshot(String indicatorSnapshot) {
        this.indicatorSnapshot = indicatorSnapshot;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    /** indicator_snapshot 反序列化用 */
    public record KdjSnapshot(BigDecimal k, BigDecimal d, BigDecimal j) {
    }
}
