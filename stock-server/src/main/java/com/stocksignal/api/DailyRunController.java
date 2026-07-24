package com.stocksignal.api;

import com.stocksignal.run.DailyRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DailyRun 手动触发（M3a）。定时调度在 M6 部署时以 cron 触发本接口。
 */
@Tag(name = "daily-run", description = "DailyRun 批处理编排")
@RestController
@RequestMapping("/api/daily-runs")
public class DailyRunController {

    private final DailyRunService dailyRunService;

    public DailyRunController(DailyRunService dailyRunService) {
        this.dailyRunService = dailyRunService;
    }

    @Operation(summary = "手动触发一次 DailyRun",
            description = "对指定市场执行 校验→评估→通知 全流程。幂等：重跑不产生重复信号、无新信号不重复推送。")
    @PostMapping
    public Map<String, String> trigger(
            @Parameter(description = "市场代码", example = "SH")
            @RequestParam String market) {
        String runId = dailyRunService.run(market);
        return Map.of("runId", runId, "market", market);
    }
}
