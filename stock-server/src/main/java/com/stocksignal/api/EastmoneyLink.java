package com.stocksignal.api;

/**
 * 东财行情页链接生成（复杂行情图跳转东财，本产品不画完整 K 线图）。
 * URL 规律：A股 quote.eastmoney.com/sh600519.html；港股 /hk/00700.html；美股 /us/AAPL.html
 */
public final class EastmoneyLink {

    private EastmoneyLink() {
    }

    public static String quoteUrl(String market, String code) {
        return switch (market) {
            case "SH", "SZ" -> "https://quote.eastmoney.com/" + market.toLowerCase() + code + ".html";
            case "HK" -> "https://quote.eastmoney.com/hk/" + code + ".html";
            case "US" -> "https://quote.eastmoney.com/us/" + code + ".html";
            default -> "https://quote.eastmoney.com/";
        };
    }
}
