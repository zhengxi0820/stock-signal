#!/usr/bin/env python3
"""fetch_index.py — 指数成分同步（akshare → MySQL index_constituents 表）

带快照日期：每次同步写入当日快照，可复现"某日的指数成分"。
用法：python fetch_index.py [--index CSI300]
"""

import argparse
import os
import sys
from datetime import date

import akshare as ak

# 指数别名 → (akshare 函数, 指数代码)
INDEXES = {
    "CSI300": ("000300", "沪深300"),
}


def fetch_constituents(index: str):
    """返回 [(market, code), ...]"""
    code, _ = INDEXES[index]
    df = ak.index_stock_cons_csindex(symbol=code)
    rows = []
    for _, r in df.iterrows():
        c = str(r["成分券代码"]).zfill(6)
        market = "SH" if c.startswith(("6", "9")) else "SZ"
        rows.append((market, c))
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description="同步指数成分到 index_constituents（带快照日期）")
    parser.add_argument("--index", default="CSI300", choices=list(INDEXES.keys()))
    args = parser.parse_args()

    import pymysql
    rows = fetch_constituents(args.index)
    today = date.today()
    print(f"[fetch-index] {args.index} 成分 {len(rows)} 只，快照日期 {today}")

    conn = pymysql.connect(
        host=os.environ["DB_HOST"], port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"],
        database=os.environ.get("DB_NAME", "stock_signal"), charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.executemany(
                "INSERT IGNORE INTO index_constituents (index_code, market, code, snapshot_date) "
                "VALUES (%s, %s, %s, %s)",
                [(args.index, m, c, today) for m, c in rows],
            )
        conn.commit()
        print(f"[fetch-index] 已写入 {len(rows)} 条（已存在忽略）")
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
