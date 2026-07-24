package com.stocksignal.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KdjCalculatorTest {

    private static Candle bar(int dayOffset, double high, double low, double close) {
        return new Candle(LocalDate.of(2024, 1, 1).plusDays(dayOffset), close, high, low, close, 1000);
    }

    @Test
    void windowInsufficient_returnsNulls() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            candles.add(bar(i, 20, 10, 15));
        }
        List<KdjPoint> kdj = new KdjCalculator().calculate(candles);
        assertEquals(8, kdj.size());
        assertTrue(kdj.stream().allMatch(p -> p == null));
    }

    @Test
    void manualRecursion_matchesHandComputedValues() {
        // high=20, low=10 恒定。第 9 根（index 8）close=15 → RSV=50，K/D 维持初值 50
        // 第 10 根（index 9）close=20 → RSV=100，K=(100+2*50)/3≈66.67，D=(66.67+2*50)/3≈55.56
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            candles.add(bar(i, 20, 10, 15));
        }
        candles.add(bar(9, 20, 10, 20));

        List<KdjPoint> kdj = new KdjCalculator().calculate(candles);

        KdjPoint p8 = kdj.get(8);
        assertEquals(50.0, p8.k(), 0.01);
        assertEquals(50.0, p8.d(), 0.01);
        assertEquals(50.0, p8.j(), 0.01);

        KdjPoint p9 = kdj.get(9);
        assertEquals(66.67, p9.k(), 0.01);
        assertEquals(55.56, p9.d(), 0.01);
        assertEquals(88.89, p9.j(), 0.01);
    }

    @Test
    void flatPrices_rsvNeutralNoNaN() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            candles.add(bar(i, 10, 10, 10)); // HHV == LLV → RSV 取 50
        }
        List<KdjPoint> kdj = new KdjCalculator().calculate(candles);
        KdjPoint last = kdj.get(14);
        assertEquals(50.0, last.k(), 0.01);
        assertEquals(50.0, last.d(), 0.01);
        assertFalse(Double.isNaN(last.j()));
    }
}
