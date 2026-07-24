package com.stocksignal.api;

import com.stocksignal.data.entity.JobRun;
import com.stocksignal.data.mapper.DailyQuoteMapper;
import com.stocksignal.data.mapper.JobRunMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运行状态：各市场最近一次 DailyRun 的成败与数据新鲜度（M6 验收项"最近成功交易日展示"）。
 */
@Tag(name = "runs", description = "DailyRun 运行状态")
@RestController
@RequestMapping("/api/runs")
public class RunStatusController {

    private final JobRunMapper jobRunMapper;
    private final DailyQuoteMapper dailyQuoteMapper;

    public RunStatusController(JobRunMapper jobRunMapper, DailyQuoteMapper dailyQuoteMapper) {
        this.jobRunMapper = jobRunMapper;
        this.dailyQuoteMapper = dailyQuoteMapper;
    }

    public record MarketStatus(String market, String runId, boolean allSuccess,
                               LocalDateTime finishedAt, LocalDate latestTradeDate,
                               List<String> phases) {
    }

    @Operation(summary = "各市场最近一次运行状态",
            description = "每市场最近一次 DailyRun 的四阶段成败、完成时间、库中最新交易日")
    @GetMapping("/latest")
    public List<MarketStatus> latest() {
        Map<String, Object> freshness = dailyQuoteMapper.findLatestTradeDatePerMarket().stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("market"),
                        m -> m.get("latest_trade_date")));

        List<MarketStatus> result = new ArrayList<>();
        for (JobRun latest : jobRunMapper.findLatestRunPerMarket()) {
            List<JobRun> phases = jobRunMapper.findByRunId(latest.getRunId());
            boolean allSuccess = !phases.isEmpty() && phases.stream()
                    .allMatch(p -> "SUCCESS".equals(p.getStatus()) || "SKIPPED".equals(p.getStatus()));
            LocalDateTime finishedAt = phases.stream()
                    .map(JobRun::getFinishedAt).filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo).orElse(null);
            Object fresh = freshness.get(latest.getMarket());
            result.add(new MarketStatus(latest.getMarket(), latest.getRunId(), allSuccess, finishedAt,
                    fresh instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) fresh,
                    phases.stream().map(p -> p.getPhase() + ":" + p.getStatus()).toList()));
        }
        return result;
    }
}
