#!/usr/bin/env python3
"""fetch_daily.py — 单只股票日线抓取（akshare → MySQL daily_quote 表）

约定（与 docs/architecture.md 一致，改动需同步更新该文档）：
- 复权口径统一前复权（adjust="qfq"）
- 股票标识：市场与代码分离传入，库内存储 market/code 两列
- 重跑安全：按 (market, code, trade_date) upsert，支持断点续灌
- 数据库连接从环境变量读取：DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD

用法：
    python fetch_daily.py --market SH --code 600519 --years 3 [--dry-run]
"""

import argparse
import os
import sys
from datetime import date, timedelta

import akshare as ak

ADJUST = "qfq"


def fetch_a_share_daily(code: str, market: str, start: date, end: date, source: str = "eastmoney"):
    """抓取 A 股（沪/深）前复权日线，返回标准 dict 列表。

    source: eastmoney（默认，akshare stock_zh_a_hist）或 sina（备用，stock_zh_a_daily）。
    两源差异：东财成交量单位为手（×100 转股）、新浪为股；新浪数据可能滞后一个交易日。
    """
    if source == "sina":
        df = ak.stock_zh_a_daily(symbol=f"{market.lower()}{code}", adjust=ADJUST)
        rows = []
        for _, r in df.iterrows():
            d = r["date"]
            if hasattr(d, "date"):
                d = d.date()
            if not (start <= d <= end):
                continue
            rows.append({
                "trade_date": d,
                "open": float(r["open"]),
                "high": float(r["high"]),
                "low": float(r["low"]),
                "close": float(r["close"]),
                "volume": int(r["volume"]),  # 新浪成交量单位为股，无需换算
            })
        return rows

    df = ak.stock_zh_a_hist(
        symbol=code,
        period="daily",
        start_date=start.strftime("%Y%m%d"),
        end_date=end.strftime("%Y%m%d"),
        adjust=ADJUST,
    )
    rows = []
    for _, r in df.iterrows():
        rows.append({
            "trade_date": r["日期"],
            "open": float(r["开盘"]),
            "high": float(r["最高"]),
            "low": float(r["最低"]),
            "close": float(r["收盘"]),
            "volume": int(r["成交量"]) * 100,  # 东财成交量单位为手，统一为股
        })
    return rows


def upsert_daily_quote(rows, market: str, code: str, source: str) -> int:
    """按 (market, code, trade_date) upsert，返回写入行数。"""
    import pymysql

    conn = pymysql.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        database=os.environ.get("DB_NAME", "stock_signal"),
        charset="utf8mb4",
    )
    sql = """
        INSERT INTO daily_quote
            (market, code, trade_date, open, high, low, close, volume, adjust, source, fetched_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE
            open=VALUES(open), high=VALUES(high), low=VALUES(low), close=VALUES(close),
            volume=VALUES(volume), adjust=VALUES(adjust), source=VALUES(source), fetched_at=NOW()
    """
    try:
        with conn.cursor() as cur:
            cur.executemany(sql, [
                (market, code, r["trade_date"], r["open"], r["high"], r["low"],
                 r["close"], r["volume"], ADJUST, source)
                for r in rows
            ])
        conn.commit()
        return len(rows)
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="抓取单只股票前复权日线并写入 daily_quote")
    parser.add_argument("--market", required=True, choices=["SH", "SZ"], help="市场（M1a 仅 A 股）")
    parser.add_argument("--code", required=True, help="股票代码，如 600519")
    parser.add_argument("--years", type=int, default=3, help="回溯年数，默认 3")
    parser.add_argument("--dry-run", action="store_true", help="只抓取打印，不写库（无需 MySQL）")
    parser.add_argument("--csv", metavar="PATH", help="导出 CSV（date,open,high,low,close,volume），供引擎离线验证")
    parser.add_argument("--source", default="eastmoney", choices=["eastmoney", "sina"],
                        help="数据源，默认东财；东财接口被限流时用 sina 兜底（数据可能滞后一个交易日）")
    args = parser.parse_args()

    end = date.today()
    start = end - timedelta(days=args.years * 365)
    source = f"akshare-{args.source}"

    print(f"[fetch] {args.market}:{args.code} {start} ~ {end} adjust={ADJUST} source={source}")
    rows = fetch_a_share_daily(args.code, args.market, start, end, args.source)
    if not rows:
        print("[fetch] 未取到数据", file=sys.stderr)
        return 1
    print(f"[fetch] 取得 {len(rows)} 行，区间 {rows[0]['trade_date']} ~ {rows[-1]['trade_date']}")
    print(f"[fetch] 首行: {rows[0]}")
    print(f"[fetch] 末行: {rows[-1]}")

    if args.dry_run:
        print("[fetch] dry-run，不写库")
        return 0

    if args.csv:
        import csv
        with open(args.csv, "w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(["date", "open", "high", "low", "close", "volume"])
            for r in rows:
                writer.writerow([r["trade_date"], r["open"], r["high"], r["low"], r["close"], r["volume"]])
        print(f"[fetch] 已导出 {len(rows)} 行到 {args.csv}")
        return 0

    n = upsert_daily_quote(rows, args.market, args.code, source)
    print(f"[fetch] 已 upsert {n} 行到 daily_quote")
    return 0


if __name__ == "__main__":
    sys.exit(main())
