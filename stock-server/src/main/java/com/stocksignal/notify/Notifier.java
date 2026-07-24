package com.stocksignal.notify;

/**
 * 通知抽象。V1 实现：控制台（开发期）、微信 PushPlus/Server酱（M5）。
 * 飞书等为平级实现。
 */
public interface Notifier {

    /**
     * 发送通知。
     *
     * @param title   标题（如 "SH 市场信号日报 2026-07-24"）
     * @param content 正文（摘要文本）
     */
    void send(String title, String content);
}
