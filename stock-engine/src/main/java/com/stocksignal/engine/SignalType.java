package com.stocksignal.engine;

/**
 * 信号类型。
 */
public enum SignalType {
    /** 黄金交叉：K 上穿 D 且交叉日 K 值 ≤ 低位阈值 */
    GOLDEN_CROSS,
    /** 死亡交叉：K 下穿 D 且交叉日 K 值 ≥ 高位阈值 */
    DEATH_CROSS
}
