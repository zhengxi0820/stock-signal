package com.stocksignal.engine;

import java.util.List;

/**
 * 策略 SPI（代码级逃生舱）：配置模型表达不了的复杂策略，可实现本接口以代码形式接入。
 * V1 只定义接口，不实现动态加载机制。
 */
public interface Strategy {

    /** 策略名，如 "kdj_cross"。 */
    String name();

    /**
     * 策略版本：由参数与过滤器组合决定，改参即变。
     * 写入 signal.strategy_version，保证历史信号可解释（哪版参数产生的）。
     */
    String version();

    /**
     * 对整段 K 线序列（升序）评估，返回全部信号（含触发时 KDJ 快照）。
     * 引擎无状态：同样的输入永远产生同样的输出。
     */
    List<EngineSignal> evaluate(List<Candle> candles);
}
