# 架构与已冻结决策

> 本文件是架构决策的唯一权威来源。修改代码行为前，先改这里。

## 模块职责

| 模块 | 职责 | 约束 |
|---|---|---|
| stock-data | 数据模型 + MyBatis Mapper | 不依赖 Web 框架 |
| stock-engine | 指标计算 + 策略引擎 | 纯库，不依赖 Spring/DB，可独立单测 |
| stock-server | REST API + 调度 + DailyRun 编排 + 通知 + 静态页面 | 业务代码只依赖 Mapper 接口 |
| fetch/ | Python + akshare 取数脚本 | 薄脚本，实现 `MarketDataProvider` 背后的数据灌入 |

## 股票标识

`市场:代码`：`SH:600519`、`SZ:000001`、`HK:00700`、`US:AAPL`。行情与信号表以 `(market, code, trade_date)` 为业务键（配置/运维表不适用）。

## 表设计（4 业务 + 1 运维）

- `daily_quote(market, code, trade_date, open, high, low, close, volume, adjust, source, fetched_at)`，主键 `(market, code, trade_date)`。复权口径统一**前复权**。成交量单位：A股/港股为股，美股为股；价格精度按市场decimal存储。
- `index_constituents(index_code, market, code, snapshot_date)` — 必须带快照日期。
- `stock_pool(pool_name, market, code)`，唯一约束；支持 `"index:CSI300"` 引用，展开后按 `(market, code)` 去重。
- `signal(market, code, trade_date, strategy, strategy_version, signal_type, indicator_snapshot JSON, run_id, generated_at)`，唯一约束 `(market, code, trade_date, strategy, strategy_version, signal_type)` — 重跑幂等。
- `job_run(run_id, market, phase, status, started_at, finished_at, stats JSON, error)` — 运维表，记录每次运行各阶段成败与数据新鲜度。

指标值不落库，查询/画图时现算；落库的只有信号。

## KDJ 计算语义（冻结）

- 输入：前复权日线，收盘价口径；默认参数 9,3,3；阈值默认高位 80 / 低位 20，均可配置。
- **中式 KDJ，手写实现，不用 TA4J 的随机指标**：RSV=(C-LLV9)/(HHV9-LLV9)×100，K=SMA(RSV,3,1)，D=SMA(K,3,1)，J=3K-2D（K/D 初值 50）。原因：TA4J 的 Stochastic 是西式定义（%D 为 %K 的 3 日简单平均），与东财口径不同，会导致交叉日期对不齐。TA4J 依赖保留给未来指标。
- 窗口数据不足 → 不出信号，不外推。
- 交叉仅以收盘后数据确认。金叉 = K 上穿 D（昨日 K≤D，今日 K>D）且交叉日 K 值 ≤ 低位阈值；死叉 = K 下穿 D（昨日 K≥D，今日 K<D）且交叉日 K 值 ≥ 高位阈值。
- 与东财比对允许数值小容差，以**交叉发生日期一致**为验收标准。

## 策略配置模型

- "策略类型 + 参数"：`type: kdj_cross` + 参数（周期、阈值、方向）+ 命名过滤器（`volume_surge`、`above_ma20`）。
- **不做通用 AND/OR 表达式语言**；出现第二个真实策略需求前不抽象。
- 策略配置归运营者管理，页面不做策略编辑。
- `Strategy` SPI 接口只定义、不实现加载（代码级逃生舱）。

## DailyRun 编排

`抓取(本市场增量) → 数据质量校验 → 入库 → 引擎评估 → 信号落库 → 推送`

- 每阶段写 `job_run`；抓取/校验失败 → 不计算、不推正常信号，改推失败告警（按 job_run 去重）。
- 任一阶段可独立重跑，全链路幂等。
- 调度按"市场 + 交易日"独立 cron 窗口（A股 17:30、港股 17:45、美股次日 08:00），引擎不含调度概念。

## 扩展点（接口即预留，不写未来实现）

- `MarketDataProvider`：数据获取（V1 实现 = 读 MySQL 中 fetch 脚本灌入的数据）。
- `Notifier`：通知（V1 实现 = 微信 PushPlus/Server酱；飞书为平级实现）。
- `Strategy` SPI：策略代码级扩展。
- REST API 为前端唯一契约：页面是静态薄客户端，可整体换壳。

## 安全边界

- 本地开发阶段页面无认证；**公网暴露前**（M6）必须加最小认证（单账号登录）+ HTTPS（Caddy）。
- MySQL 不对公网开放，仅本机访问。
