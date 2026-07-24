package com.stocksignal.run;

import com.stocksignal.data.mapper.JobRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时清理僵死运行：单实例应用，进程启动时不存在合法的进行中运行，
 * 凡 RUNNING 阶段一律标记 FAILED（上次进程被杀/崩溃的残留），
 * 防止僵死 RUNNING 阻塞后续触发（并发保护依赖该状态）。
 */
@Component
public class StaleRunCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleRunCleanup.class);

    private final JobRunMapper jobRunMapper;

    public StaleRunCleanup(JobRunMapper jobRunMapper) {
        this.jobRunMapper = jobRunMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int cleaned = jobRunMapper.failStaleRunning("interrupted: process restarted");
        if (cleaned > 0) {
            log.info("清理僵死运行阶段 {} 条（上次进程中断残留）", cleaned);
        }
    }
}
