# fetch/ — 数据取数脚本（Python + akshare）

薄脚本层：从公开行情接口抓取日线数据与指数成分，写入 MySQL。
设计上它是 `MarketDataProvider` 背后的数据灌入实现，与 Java 主体通过数据库解耦。

## 环境

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## 约定

- 复权口径统一**前复权**（akshare `adjust="qfq"`）
- 股票标识与 Java 侧一致：`SH:600519` / `HK:00700` / `US:AAPL`
- 重跑安全：按 `(market, code, trade_date)` 去重 upsert，支持断点续灌
- 数据库连接从环境变量读取（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`），严禁硬编码入仓

## 用法

```bash
# 干跑验证（不需要 MySQL）：抓取并打印样例行
python fetch_daily.py --market SH --code 600519 --years 3 --dry-run

# 东财接口被限流时切换备用源（sina 可能滞后一个交易日；tencent 已实测与 sina 数值一致）
python fetch_daily.py --market SH --code 600519 --years 3 --source sina --dry-run
python fetch_daily.py --market SH --code 600519 --years 3 --source tencent --dry-run

# 港股/美股（依赖东财接口，无备用源；本机被限流时需待解封或在服务器上执行）
python fetch_daily.py --market HK --code 00700 --years 3
python fetch_daily.py --market US --code AAPL --years 3

# 导出 CSV 供引擎离线验证（KdjSignalTool）
python fetch_daily.py --market SH --code 600519 --years 3 --csv out.csv

# 写入 daily_quote 表（重跑安全，按 trade_date upsert）
python fetch_daily.py --market SH --code 600519 --years 3

# 同步指数成分（沪深300，带快照日期）
python fetch_index.py --index CSI300

# 批量增量抓取（限流间隔+退避重试+东财/新浪自动切换+断点续灌）
python fetch_batch.py --index CSI300 --interval 2.5
python fetch_batch.py --stocks SH:600519 SZ:000001
```
