package com.stocksignal.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KdjCrossStrategyTest {

    private static final LocalDate BASE = LocalDate.of(2024, 1, 1);

    private static Candle bar(int i, double high, double low, double close, long volume) {
        return new Candle(BASE.plusDays(i), close, high, low, close, volume);
    }

    /** 30 根持续走低（收在最低，RSV=0，K/D 衰减至接近 0 且 K<D），再追加给定尾巴。 */
    private static List<Candle> declineThen(Candle... tail) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(bar(i, 20, 10, 10, 100));
        }
        int n = candles.size();
        for (int t = 0; t < tail.length; t++) {
            Candle c = tail[t];
            candles.add(new Candle(BASE.plusDays(n + t), c.open(), c.high(), c.low(), c.close(), c.volume()));
        }
        return candles;
    }

    /** 30 根持续走高（收在最高，RSV=100，K/D 升至接近 100 且 K>D），再追加给定尾巴。 */
    private static List<Candle> riseThen(Candle... tail) {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(bar(i, 20, 10, 20, 100));
        }
        int n = candles.size();
        for (int t = 0; t < tail.length; t++) {
            Candle c = tail[t];
            candles.add(new Candle(BASE.plusDays(n + t), c.open(), c.high(), c.low(), c.close(), c.volume()));
        }
        return candles;
    }

    @Test
    void goldenCross_inLowZone_producesSignal() {
        // 反转根 close=14 → RSV=40，K≈13.3（≤20）上穿 D → 金叉
        List<Candle> candles = declineThen(bar(0, 20, 10, 14, 100));
        List<EngineSignal> signals = new KdjCrossStrategy().evaluate(candles);

        assertEquals(1, signals.size());
        EngineSignal s = signals.get(0);
        assertEquals(SignalType.GOLDEN_CROSS, s.type());
        assertEquals(BASE.plusDays(30), s.tradeDate());
        assertTrue(s.k() <= 20, "交叉日 K 值应在低位区，实际 " + s.k());
        assertTrue(s.k() > s.d());
    }

    @Test
    void goldenCross_strongReversalButKAboveThreshold_suppressed() {
        // 反转根 close=20 → RSV=100，K≈33（>20）→ 不满足低位条件，不出信号
        List<Candle> candles = declineThen(bar(0, 20, 10, 20, 100));
        assertTrue(new KdjCrossStrategy().evaluate(candles).isEmpty());
    }

    @Test
    void deathCross_inHighZone_producesSignal() {
        // 回落根 close=16 → RSV=60，K≈86.7（≥80）下穿 D → 死叉
        List<Candle> candles = riseThen(bar(0, 20, 10, 16, 100));
        List<EngineSignal> signals = new KdjCrossStrategy().evaluate(candles);

        assertEquals(1, signals.size());
        EngineSignal s = signals.get(0);
        assertEquals(SignalType.DEATH_CROSS, s.type());
        assertTrue(s.k() >= 80, "交叉日 K 值应在高位区，实际 " + s.k());
        assertTrue(s.k() < s.d());
    }

    @Test
    void deathCross_sharpDropButKBelowThreshold_suppressed() {
        // 回落根 close=10 → RSV=0，K≈66.7（<80）→ 不满足高位条件，不出信号
        List<Candle> candles = riseThen(bar(0, 20, 10, 10, 100));
        assertTrue(new KdjCrossStrategy().evaluate(candles).isEmpty());
    }

    @Test
    void steadySeries_noSignal() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            candles.add(bar(i, 20, 10, 15, 100)); // RSV=50 恒定，K=D=50，无交叉
        }
        assertTrue(new KdjCrossStrategy().evaluate(candles).isEmpty());
    }

    @Test
    void volumeSurgeFilter_blocksWhenNoSurge() {
        // 同样的金叉形态，反转根未放量（100 vs 前5日均量100，未超1.5倍）→ 被过滤
        List<Candle> candles = declineThen(bar(0, 20, 10, 14, 100));
        KdjCrossStrategy strategy = new KdjCrossStrategy(9, 3, 3, 20, 80, List.of(new VolumeSurgeFilter()));
        assertTrue(strategy.evaluate(candles).isEmpty());
    }

    @Test
    void volumeSurgeFilter_passesWhenSurge() {
        // 反转根放量 200 > 100×1.5 → 放行
        List<Candle> candles = declineThen(bar(0, 20, 10, 14, 200));
        KdjCrossStrategy strategy = new KdjCrossStrategy(9, 3, 3, 20, 80, List.of(new VolumeSurgeFilter()));
        assertEquals(1, strategy.evaluate(candles).size());
    }

    @Test
    void aboveMaFilter_insufficientWindow_blocks() {
        // 金叉形态存在，但 MA 周期 40 > 序列长度 31 → 窗口不足，过滤器保守拦截
        List<Candle> candles = declineThen(bar(0, 20, 10, 14, 100));
        KdjCrossStrategy strategy = new KdjCrossStrategy(9, 3, 3, 20, 80, List.of(new AboveMaFilter(40)));
        assertTrue(strategy.evaluate(candles).isEmpty());
    }

    @Test
    void aboveMaFilter_passesWhenCloseAboveMa() {
        // 同样的金叉形态，MA20≈10.2 < 收盘价 14 → 放行
        List<Candle> candles = declineThen(bar(0, 20, 10, 14, 100));
        KdjCrossStrategy strategy = new KdjCrossStrategy(9, 3, 3, 20, 80, List.of(new AboveMaFilter()));
        assertEquals(1, strategy.evaluate(candles).size());
    }

    @Test
    void version_changesWithParamsAndFilters() {
        assertEquals("kdj_cross:th=20.0/80.0", new KdjCrossStrategy().version());
        String withFilters = new KdjCrossStrategy(9, 3, 3, 20, 80,
                List.of(new VolumeSurgeFilter(), new AboveMaFilter())).version();
        assertEquals("kdj_cross:th=20.0/80.0:f=volume_surge,above_ma20", withFilters);
    }
}
