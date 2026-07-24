package com.stocksignal.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * FETCH 阶段：调用 fetch/fetch_batch.py 对指定股票做增量抓取。
 * Java 与 Python 薄脚本通过进程边界解耦（脚本本身幂等、带限流/退避/双源切换）。
 */
@Component
public class FetchRunner {

    private static final Logger log = LoggerFactory.getLogger(FetchRunner.class);
    private static final int TIMEOUT_MINUTES = 120;

    private final String pythonPath;
    private final String scriptDir;

    public FetchRunner(
            @Value("${stock.fetch.python-path:fetch/.venv/Scripts/python.exe}") String pythonPath,
            @Value("${stock.fetch.script-dir:fetch}") String scriptDir) {
        this.pythonPath = pythonPath;
        this.scriptDir = scriptDir;
    }

    public record FetchResult(boolean success, int stockCount, String tail) {
    }

    /**
     * 对一批股票执行增量抓取（同市场）。
     *
     * @param market 市场代码（SH/SZ）
     * @param codes  股票代码集合
     */
    public FetchResult run(String market, Set<String> codes) {
        if (codes.isEmpty()) {
            return new FetchResult(true, 0, "pool empty");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPath);
        cmd.add("fetch_batch.py");
        cmd.add("--stocks");
        codes.forEach(c -> cmd.add(market + ":" + c));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Path.of(scriptDir).toAbsolutePath().toFile());
        pb.redirectErrorStream(true);
        StringBuilder tail = new StringBuilder();
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    tail.append(line).append('\n');
                    if (tail.length() > 4000) {
                        tail.delete(0, tail.length() - 4000);
                    }
                }
            }
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return new FetchResult(false, codes.size(), "timeout");
            }
            int exit = process.exitValue();
            // 脚本约定：0=全部成功，2=部分失败（仍视为完成，覆盖率由 VALIDATE 把关）
            return new FetchResult(exit == 0 || exit == 2, codes.size(), tail.toString());
        } catch (Exception e) {
            log.error("FETCH 脚本执行异常: {}", e.getMessage());
            return new FetchResult(false, codes.size(), e.getMessage());
        }
    }
}
