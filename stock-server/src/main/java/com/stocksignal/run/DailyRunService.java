package com.stocksignal.run;

import com.stocksignal.data.entity.DailyQuote;
import com.stocksignal.data.entity.JobRun;
import com.stocksignal.data.entity.Signal;
import com.stocksignal.data.mapper.DailyQuoteMapper;
import com.stocksignal.data.mapper.IndexConstituentsMapper;
import com.stocksignal.data.mapper.JobRunMapper;
import com.stocksignal.data.mapper.SignalMapper;
import com.stocksignal.data.mapper.StockPoolMapper;
import com.stocksignal.data.provider.MarketDataProvider;
import com.stocksignal.engine.Candle;
import com.stocksignal.engine.EngineSignal;
import com.stocksignal.engine.Strategy;
import com.stocksignal.notify.Notifier;
import com.stocksignal.strategy.StrategyConfigLoader;
import com.stocksignal.strategy.StrategyDefinition;
import com.stocksignal.strategy.StrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DailyRun 编排（显式状态机）：
 * VALIDATE(数据新鲜度) → EVALUATE(引擎评估+信号幂等落库) → NOTIFY(摘要推送)。
 *
 * <p>规则（docs/architecture.md「DailyRun 编排」，改动需先改该文档）：
 * <ul>
 *   <li>每阶段写 job_run；任一前置阶段失败 → 不执行后续，改推失败告警</li>
 *   <li>全链路幂等：信号用 INSERT IGNORE，重跑不产生重复行、不重复推送新信号</li>
 * </ul>
 */
@Service
public class DailyRunService {

    private static final Logger log = LoggerFactory.getLogger(DailyRunService.class);
    private static final int LOOKBACK_YEARS = 3;

    private final MarketDataProvider marketDataProvider;
    private final DailyQuoteMapper dailyQuoteMapper;
    private final SignalMapper signalMapper;
    private final JobRunMapper jobRunMapper;
    private final StrategyConfigLoader configLoader;
    private final Notifier notifier;
    private final StockPoolMapper stockPoolMapper;
    private final IndexConstituentsMapper indexConstituentsMapper;
    private final FetchRunner fetchRunner;

    public DailyRunService(MarketDataProvider marketDataProvider,
                           DailyQuoteMapper dailyQuoteMapper,
                           SignalMapper signalMapper,
                           JobRunMapper jobRunMapper,
                           StrategyConfigLoader configLoader,
                           Notifier notifier,
                           StockPoolMapper stockPoolMapper,
                           IndexConstituentsMapper indexConstituentsMapper,
                           FetchRunner fetchRunner) {
        this.marketDataProvider = marketDataProvider;
        this.dailyQuoteMapper = dailyQuoteMapper;
        this.signalMapper = signalMapper;
        this.jobRunMapper = jobRunMapper;
        this.configLoader = configLoader;
        this.notifier = notifier;
        this.stockPoolMapper = stockPoolMapper;
        this.indexConstituentsMapper = indexConstituentsMapper;
        this.fetchRunner = fetchRunner;
    }

    /**
     * 对指定市场执行一次 DailyRun，返回 runId。
     */
    public String run(String market) {
        String runId = UUID.randomUUID().toString();
        log.info("DailyRun 开始 runId={} market={}", runId, market);

        StrategyConfigLoader.Config config = configLoader.load();
        Set<String> pool = resolvePool(config, market);
        if (pool.isEmpty()) {
            finishPhase(runId, market, "VALIDATE", "SKIPPED", "{\"reason\":\"pool empty\"}", null);
            return runId;
        }

        if (!fetch(runId, market, pool)) {
            notifier.send("【告警】" + market + " 数据抓取失败",
                    "runId=" + runId + "，FETCH 阶段失败，本次不计算不推送信号。详见 job_run 表。");
            return runId;
        }

        if (!validate(runId, market, pool)) {
            notifier.send("【告警】" + market + " 数据校验失败",
                    "runId=" + runId + "，数据新鲜度校验未通过，本次不计算不推送信号。详见 job_run 表。");
            return runId;
        }

        List<Signal> newSignals = evaluate(runId, market, pool, config, runId);

        notify(runId, market, newSignals);
        log.info("DailyRun 结束 runId={} market={} 新信号={}", runId, market, newSignals.size());
        return runId;
    }

    /** 展开该市场池内股票：策略引用的池，YAML 条目 ∪ stock_pool 表条目 ∪ "index:" 引用（最新快照成分）。 */
    private Set<String> resolvePool(StrategyConfigLoader.Config config, String market) {
        Set<String> poolNames = config.strategies().stream()
                .map(StrategyDefinition::pool).collect(java.util.stream.Collectors.toSet());
        Set<String> codes = new LinkedHashSet<>();
        for (String poolName : poolNames) {
            for (String e : config.pools().getOrDefault(poolName, List.of())) {
                if (e.startsWith("index:")) {
                    String indexCode = e.substring("index:".length());
                    for (var c : indexConstituentsMapper.findLatestSnapshot(indexCode)) {
                        if (c.getMarket().equals(market)) {
                            codes.add(c.getCode());
                        }
                    }
                    continue;
                }
                String[] parts = e.split(":");
                if (parts.length == 2 && parts[0].equals(market)) {
                    codes.add(parts[1]);
                }
            }
            for (var entry : stockPoolMapper.findByPool(poolName)) {
                if (entry.getMarket().equals(market)) {
                    codes.add(entry.getCode());
                }
            }
        }
        return codes;
    }

    /** FETCH：调用 fetch 脚本对池内股票做增量抓取（脚本幂等，含限流/退避/双源切换）。 */
    private boolean fetch(String runId, String market, Set<String> pool) {
        startPhase(runId, market, "FETCH");
        FetchRunner.FetchResult result = fetchRunner.run(market, pool);
        finishPhase(runId, market, "FETCH", result.success() ? "SUCCESS" : "FAILED",
                "{\"poolSize\":" + result.stockCount() + "}",
                result.success() ? null : result.tail());
        return result.success();
    }

    /** VALIDATE：池内每只股票有数据且最新交易日在 7 天内。 */
    private boolean validate(String runId, String market, Set<String> pool) {
        JobRun phase = startPhase(runId, market, "VALIDATE");
        List<String> stale = new ArrayList<>();
        LocalDate threshold = LocalDate.now().minusDays(7);
        for (String code : pool) {
            LocalDate latest = dailyQuoteMapper.findLatestTradeDate(market, code);
            if (latest == null || latest.isBefore(threshold)) {
                stale.add(code + "(" + latest + ")");
            }
        }
        boolean ok = stale.isEmpty();
        finishPhase(runId, market, "VALIDATE", ok ? "SUCCESS" : "FAILED",
                "{\"poolSize\":" + pool.size() + ",\"stale\":" + stale.size() + "}",
                ok ? null : "数据缺失或过期: " + stale);
        return ok;
    }

    /** EVALUATE：逐股评估全部策略，信号幂等落库，返回本次新增信号。 */
    private List<Signal> evaluate(String runId, String market, Set<String> pool,
                                  StrategyConfigLoader.Config config, String runIdForSignal) {
        startPhase(runId, market, "EVALUATE");
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusYears(LOOKBACK_YEARS);
        List<Signal> newSignals = new ArrayList<>();
        int evaluated = 0;
        try {
            for (String code : pool) {
                List<Candle> candles = marketDataProvider.getDailyQuotes(market, code, start, end)
                        .stream().map(this::toCandle).toList();
                if (candles.isEmpty()) {
                    continue;
                }
                evaluated++;
                for (StrategyDefinition def : config.strategies()) {
                    Strategy strategy = StrategyFactory.build(def);
                    for (EngineSignal es : strategy.evaluate(candles)) {
                        Signal s = new Signal();
                        s.setMarket(market);
                        s.setCode(code);
                        s.setTradeDate(es.tradeDate());
                        s.setStrategy(def.name());
                        s.setStrategyVersion(strategy.version());
                        s.setSignalType(es.type().name());
                        s.setIndicatorSnapshot(String.format("{\"K\":%.2f,\"D\":%.2f,\"J\":%.2f}", es.k(), es.d(), es.j()));
                        s.setRunId(runIdForSignal);
                        if (signalMapper.insertIgnore(s) > 0) {
                            newSignals.add(s);
                        }
                    }
                }
            }
            finishPhase(runId, market, "EVALUATE", "SUCCESS",
                    "{\"evaluated\":" + evaluated + ",\"newSignals\":" + newSignals.size() + "}", null);
            return newSignals;
        } catch (Exception e) {
            finishPhase(runId, market, "EVALUATE", "FAILED", null, e.getMessage());
            throw e;
        }
    }

    /** NOTIFY：仅当本次有新增信号时推送摘要（重跑无新信号 → 不重复打扰）。 */
    private void notify(String runId, String market, List<Signal> newSignals) {
        startPhase(runId, market, "NOTIFY");
        if (newSignals.isEmpty()) {
            finishPhase(runId, market, "NOTIFY", "SKIPPED", "{\"reason\":\"no new signals\"}", null);
            return;
        }
        StringBuilder sb = new StringBuilder();
        newSignals.stream().limit(50).forEach(s -> sb.append(String.format("%s %s %s %s%n",
                s.getTradeDate(), s.getMarket() + ":" + s.getCode(), s.getSignalType(), s.getIndicatorSnapshot())));
        if (newSignals.size() > 50) {
            sb.append("... 共 ").append(newSignals.size()).append(" 条");
        }
        notifier.send(market + " 市场信号（新增 " + newSignals.size() + " 条）", sb.toString());
        finishPhase(runId, market, "NOTIFY", "SUCCESS", "{\"sent\":" + newSignals.size() + "}", null);
    }

    private Candle toCandle(DailyQuote q) {
        return new Candle(q.getTradeDate(),
                q.getOpen().doubleValue(), q.getHigh().doubleValue(),
                q.getLow().doubleValue(), q.getClose().doubleValue(), q.getVolume());
    }

    private JobRun startPhase(String runId, String market, String phase) {
        JobRun jr = new JobRun();
        jr.setRunId(runId);
        jr.setMarket(market);
        jr.setPhase(phase);
        jobRunMapper.upsertRunning(jr);
        return jr;
    }

    private void finishPhase(String runId, String market, String phase,
                             String status, String stats, String error) {
        JobRun jr = new JobRun();
        jr.setRunId(runId);
        jr.setMarket(market);
        jr.setPhase(phase);
        jr.setStatus(status);
        jr.setStats(stats);
        jr.setError(error);
        jobRunMapper.finish(jr);
    }
}
