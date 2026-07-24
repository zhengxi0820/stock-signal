package com.stocksignal.engine.tools;

import com.stocksignal.engine.Candle;
import com.stocksignal.engine.EngineSignal;
import com.stocksignal.engine.KdjCrossStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线验证工具：读取 fetch 脚本导出的 CSV（date,open,high,low,close,volume），
 * 用 KDJ 交叉策略评估并打印全部信号，用于与东财 KDJ 交叉日期人工比对。
 *
 * <p>用法：java com.stocksignal.engine.tools.KdjSignalTool &lt;csv路径&gt; [最近N条]
 */
public final class KdjSignalTool {

    private KdjSignalTool() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: KdjSignalTool <csv> [lastN]");
            System.exit(1);
        }
        List<Candle> candles = readCsv(Path.of(args[0]));
        int lastN = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        List<EngineSignal> signals = new KdjCrossStrategy().evaluate(candles);
        System.out.printf("共 %d 根K线，%d 个信号。最近 %d 个：%n", candles.size(), signals.size(),
                Math.min(lastN, signals.size()));
        signals.stream().skip(Math.max(0, signals.size() - lastN)).forEach(s ->
                System.out.printf("%s  %-12s  K=%.2f D=%.2f J=%.2f%n",
                        s.tradeDate(), s.type(), s.k(), s.d(), s.j()));
    }

    private static List<Candle> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<Candle> candles = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // 跳过表头
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(",");
            candles.add(new Candle(
                    LocalDate.parse(f[0]),
                    Double.parseDouble(f[1]),
                    Double.parseDouble(f[2]),
                    Double.parseDouble(f[3]),
                    Double.parseDouble(f[4]),
                    Long.parseLong(f[5])));
        }
        return candles;
    }
}
