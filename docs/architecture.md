# 架构与已冻结决策

> 本文件是架构决策的唯一权威来源。修改代码行为前，先改这里。

## 模块职责

| 模块 | 职责 | 约束 |
|---|---|---|
| stock-data | 数据模型 + MyBatis Mapper | 不依赖 Web 框架 |
| stock-engine | 指标计算 + 策略引擎 | 纯库，不依赖 Spring/DB，可独立单测 |
| stock-server | REST API + DailyRun 编排 + 通知 + 认证 + 静态页面 | 业务代码只依赖 Mapper 接口 |
| fetch/ | Python + akshare 取数脚本 | 薄脚本，实现 `MarketDataProvider` 背后的数据灌入 |

## 股票标识

`市场:代码`：`SH:600519`、`SZ:000001`、`HK:00700`、`US:AAPL`。行情与信号表以 `(market, code, trade_date)` 为业务键（配置/运维表不适用）。

## 表设计（4 业务 + 1 运维）

- `daily_quote(market, code, trade_date, open, high, low, close, volume, adjust, source, fetched_at)`，主键 `(market, code, trade_date)`。复权口径统一**前复权**。成交量单位：A股/美股为股；港股待校验（见「数据源」节）。价格按市场货币 decimal 存储。
- `index_constituents(index_code, market, code, snapshot_date)` — 必须带快照日期。
- `stock_pool(pool_name, market, code)`，唯一约束；支持 `"index:CSI300"` 引用，展开后按 `(market, code)` 去重。**池成员 = YAML `pools` 条目 ∪ `stock_pool` 表条目**（YAML 归运营者，表由页面管理）。
- `signals(market, code, trade_date, strategy, strategy_version, signal_type, indicator_snapshot JSON, run_id, generated_at)`，唯一约束 `(market, code, trade_date, strategy, strategy_version, signal_type)` — 重跑幂等。（表名为 `signals`：`signal` 是 MySQL 保留字。）
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

四阶段状态机：`FETCH(调 fetch 脚本增量抓取入库) → VALIDATE(数据质量校验) → EVALUATE(引擎评估+信号落库) → NOTIFY(摘要推送)`

- 每阶段写 `job_run`；任一前置阶段失败 → 不执行后续，改推失败告警。
- 任一阶段可独立重跑，全链路幂等。
- EVALUATE 对整段历史序列评估，`signals` 表保存**全部历史信号**（INSERT IGNORE 幂等）；首次运行回填历史，之后每次只新增。
- 并发保护：同一市场已有进行中运行（2 小时内 RUNNING 阶段）或 10 分钟冷却期内，拒绝触发（409）。**应用启动时把全部残留 RUNNING 阶段标记 FAILED**（`StaleRunCleanup`：单实例应用启动时不存在合法进行中运行，防止进程被杀后僵死 RUNNING 阻塞该市场）。
- NOTIFY 仅在本次有**新增**信号时推送，重跑无新信号 → 不重复打扰。
- 通知实现按配置选择：`stock.notifier=console`（默认，开发期）/ `wechat`（PushPlus，token 走环境变量 `PUSHPLUS_TOKEN`）。
- 运行状态对外暴露：`GET /api/runs/latest` 返回各市场最近一次运行四阶段成败与数据新鲜度（页面顶部状态条）。
- 调度按"市场 + 交易日"独立 cron 窗口（SH 17:30、SZ 17:45、HK 17:50、US 次日 08:00，见 deploy/crontab.example），引擎不含调度概念。

## 前端大屏口径（冻结）

- "当前金叉/死叉状态"：每只股票每个策略取 `signals` 中**最近一次**信号，最近信号为金叉 → 当前金叉状态，反之死叉。
- 大屏按股票聚合，多策略以标记（badge）叠加展示；新增策略后自动并入，无需改口径。

## 数据源（冻结）

- A 股：东财（主）/ 新浪 / 腾讯三源切换（`fetch_batch.py` 带健康记忆：东财连续失败 2 次后备用源优先）。东财/腾讯成交量单位为手需换算、新浪为股；腾讯接口实测单位为股。
- 港股/美股：仅东财接口（`stock_hk_hist` / `stock_us_hist`），无备用源；东财对 IP 限流时表现为连接重置，待解封或在服务器执行。美股代码通过东财现货列表映射 secid（AAPL → 105.AAPL，进程内缓存）。
- 已知待校验项：港股成交量单位（股/手）待东财可访问时验证（代码中有 TODO 标记）。
- 所有脚本强制直连（`NO_PROXY=*`）：requests 会读取系统代理，代理开启时抓取会失败。

## 扩展点（接口即预留，不写未来实现）

- `MarketDataProvider`：数据获取（V1 实现 = 读 MySQL 中 fetch 脚本灌入的数据）。
- `Notifier`：通知（V1 实现 = Console（默认）/ 微信 PushPlus；飞书、Server酱为平级扩展）。
- `Strategy` SPI：策略代码级扩展。
- REST API 为前端唯一契约：页面是静态薄客户端，可整体换壳。

## 安全边界

- 认证已实现（单账号口令，`AUTH_TOKEN` 环境变量启用，登录换 HttpOnly 派生 Cookie）：本机开发默认关闭，**公网部署必须启用**并配 HTTPS（Caddy 自动证书，见 deploy/README.md 安全检查单）。
- 防爆破：登录同一 IP 连续失败 5 次锁定 15 分钟（429），成功/失败写审计日志（`AUTH login success/fail ip=...`，可对接 fail2ban）。
- Cookie 加固：HttpOnly + SameSite=Strict + Secure（本地 HTTP 开发可用 `SECURE_COOKIE=false` 关闭 Secure）。
- 信息收口：认证启用后 Swagger/api-docs 也需登录，生产可用 `SWAGGER_ENABLED=false` 整体关闭。
- 安全响应头在 Caddy 层下发（X-Frame-Options/nosniff/Referrer-Policy/CSP，见 deploy/Caddyfile）。
- 批处理防滥用：同一市场已有运行进行中（2 小时内 RUNNING 阶段）或距上次开始不足 10 分钟冷却期时，拒绝重复触发 DailyRun（409）。
- MySQL 不对公网开放，仅本机访问。
