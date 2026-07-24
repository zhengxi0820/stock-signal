#!/usr/bin/env python3
"""fetch_batch.py — 批量日线抓取（沪深300 扩容用）

特性（对应风险表"akshare 限流/封 IP"对策）：
- 增量抓取：按 daily_quote 中该股最新 trade_date 续抓，无数据抓 3 年
- 每请求间隔（默认 2.5 秒）；失败指数退避重试 3 次
- 东财失败自动切换新浪源（两源口径见 fetch_daily.py 注释）
- 断点续灌：upsert 幂等，中断后重跑从失败处继续
- 结束时打印覆盖率统计（成功/总数）

用法：
    python fetch_batch.py --index CSI300          # 抓指数最新快照的全部成分
    python fetch_batch.py --stocks SH:600519 SZ:000001
"""

import argparse
import os
import sys
import time
from datetime import date, timedelta

import pymysql

from fetch_daily import fetch_daily, upsert_daily_quote

YEARS_HISTORY = 3
RETRY = 3


class SourceState:
    """源健康记忆：东财连续失败 2 次后优先用新浪，避免每只股票都白等退避时间。"""
    eastmoney_failures = 0


def get_conn():
    return pymysql.connect(
        host=os.environ["DB_HOST"], port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
        database=os.environ.get("DB_NAME", "stock_signal"), charset="utf8mb4",
    )


def load_index_stocks(index: str):
    """读取指数最新快照的成分列表。"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT market, code FROM index_constituents "
                "WHERE index_code=%s AND snapshot_date=(SELECT MAX(snapshot_date) FROM index_constituents WHERE index_code=%s)",
                (index, index),
            )
            return [(m, c) for m, c in cur.fetchall()]
    finally:
        conn.close()


def latest_trade_date(market: str, code: str):
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT MAX(trade_date) FROM daily_quote WHERE market=%s AND code=%s", (market, code))
            return cur.fetchone()[0]
    finally:
        conn.close()


def fetch_one(market: str, code: str, verbose=True):
    """单股增量抓取，东财失败切新浪，指数退避重试。返回 (成功与否, 行数, 使用源)。"""
    latest = latest_trade_date(market, code)
    end = date.today()
    start = (latest + timedelta(days=1)) if latest else end - timedelta(days=YEARS_HISTORY * 365)
    if start > end:
        return True, 0, "up-to-date"

    # A 股三源切换（带健康记忆）；HK/US 只有东财源
    if market in ("SH", "SZ"):
        sources = ["eastmoney", "sina", "tencent"] if SourceState.eastmoney_failures < 2 \
            else ["sina", "tencent", "eastmoney"]
    else:
        sources = ["eastmoney"]
    for source in sources:
        for attempt in range(RETRY):
            try:
                rows = fetch_daily(market, code, start, end, source)
                if source == "eastmoney":
                    SourceState.eastmoney_failures = 0
                if not rows:
                    return True, 0, source
                n = upsert_daily_quote(rows, market, code, f"akshare-{source}")
                return True, n, source
            except Exception as e:
                wait = 2 ** attempt * 5
                if verbose:
                    print(f"  [retry] {market}:{code} {source} 第{attempt + 1}次失败({type(e).__name__})，{wait}s 后重试")
                time.sleep(wait)
        if source == "eastmoney":
            SourceState.eastmoney_failures += 1
    return False, 0, "all-failed"


def main() -> int:
    parser = argparse.ArgumentParser(description="批量增量抓取日线，带限流/退避/双源切换")
    parser.add_argument("--index", help="指数代码（抓最新快照成分），如 CSI300")
    parser.add_argument("--stocks", nargs="*", help="或直接给股票列表，如 SH:600519 SZ:000001")
    parser.add_argument("--interval", type=float, default=2.5, help="每请求间隔秒数，默认 2.5")
    args = parser.parse_args()

    if args.index:
        stocks = load_index_stocks(args.index)
    elif args.stocks:
        stocks = [(s.split(":")[0], s.split(":")[1]) for s in args.stocks]
    else:
        print("需要 --index 或 --stocks", file=sys.stderr)
        return 1

    total = len(stocks)
    ok, failed, rows_total = 0, [], 0
    print(f"[batch] 共 {total} 只，间隔 {args.interval}s")
    for i, (market, code) in enumerate(stocks, 1):
        success, n, source = fetch_one(market, code)
        rows_total += n
        if success:
            ok += 1
            if n > 0:
                print(f"[batch] {i}/{total} {market}:{code} +{n} 行 ({source})")
            else:
                print(f"[batch] {i}/{total} {market}:{code} 已最新 ({source})")
        else:
            failed.append(f"{market}:{code}")
            print(f"[batch] {i}/{total} {market}:{code} 失败（双源均不可用）")
        time.sleep(args.interval)

    print(f"[batch] 完成：成功 {ok}/{total}，新增 {rows_total} 行，失败 {len(failed)} 只: {failed}")
    return 0 if not failed else 2


if __name__ == "__main__":
    sys.exit(main())
