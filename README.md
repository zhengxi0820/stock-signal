# stock-signal

多市场（沪深港美）盘后信号系统：每日收盘后扫描股票池，用可配置策略（KDJ 金叉死叉等）筛出候选股，页面大屏展示 + 微信推送通知。

## 功能（V1）

- **金叉大屏**：当前处于金叉/死叉状态的股票一览（每股票每策略取最近信号判定，多策略标记叠加）
- **每日信号**：盘后自动扫描沪深300 + 自选池，新信号落库并推送
- **指标图**：KDJ 曲线 + 收盘价，悬浮查看每日数值；复杂行情一键跳转东财
- **股票池管理**：页面加删自选（YAML 池 ∪ 数据库池 ∪ 指数成分引用）
- **微信推送**：PushPlus webhook，新增信号摘要 + 失败告警
- **可靠批处理**：FETCH → VALIDATE → EVALUATE → NOTIFY 四阶段编排，全链路幂等可重跑，job_run 全程可追溯

## 架构

```
stock-parent
├── stock-data      数据模型 + MyBatis Mapper（业务代码只依赖 Mapper 接口）
├── stock-engine    指标计算 + 策略引擎（纯库，无 Spring/DB 依赖，13 个单测）
├── stock-server    REST API + DailyRun 编排 + 通知 + 认证 + 静态页面
├── fetch/          Python + akshare 取数脚本（东财/新浪/腾讯三源切换，限流退避，断点续灌）
├── deploy/         部署手册、Caddy、systemd、备份、cron 示例
└── docs/           架构决策（权威来源）+ DDL + 交接进度 + 踩坑记录 + 知识手册
```

设计原则与全部已冻结决策见 `docs/architecture.md`；贡献约定见 `AGENTS.md`（核心：**文档即契约，接口/行为变更必须同次更新文档**）。

## 快速开始（本地开发）

前置：JDK 17、Maven、Python 3.10+、MySQL 8（库表用 `docs/db/schema.sql` 初始化，应用账号 `stock/stock` 或自建）。

```bash
# 1. Python 取数环境
cd fetch && python -m venv .venv && .venv/Scripts/activate  # Windows；Linux 用 .venv/bin/activate
pip install -r requirements.txt

# 2. 抓取数据（沪深300 成分 + 全量日线，约 20~40 分钟，带限流）
python fetch_index.py --index CSI300
python fetch_batch.py --index CSI300

# 3. 构建并启动（回到仓库根；FETCH_* 指向 fetch 脚本，Windows 路径示例如下）
cd ..
mvn install
export FETCH_PYTHON="$PWD/fetch/.venv/Scripts/python.exe"  # Linux 为 fetch/.venv/bin/python
export FETCH_DIR="$PWD/fetch"
mvn -pl stock-server spring-boot:run

# 4. 触发一次全市场扫描（或等部署后 cron 定时触发）
curl -X POST "http://localhost:8080/api/daily-runs?market=SH"
curl -X POST "http://localhost:8080/api/daily-runs?market=SZ"
```

- 页面：`http://localhost:8080`（金叉大屏 / 今日信号 / 指标图 / 历史查询 / 股票池管理）
- API 文档：`http://localhost:8080/swagger-ui.html`（springdoc 从注解生成）

## 配置

全部走环境变量（见 `deploy/env.sh.example`）：

| 变量 | 说明 | 缺省 |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | MySQL 连接 | localhost 本地库 |
| `FETCH_PYTHON` / `FETCH_DIR` | fetch 脚本路径 | fetch/.venv 相对路径 |
| `NOTIFIER` / `PUSHPLUS_TOKEN` | 通知实现与微信 token | console |
| `AUTH_TOKEN` | 页面访问口令（**公网必须配置**） | 空 = 认证关闭（仅本机） |
| `SECURE_COOKIE` | Cookie 是否带 Secure（HTTPS 生产保持 true；本地 HTTP 调试且启用认证时设 false） | true |
| `SWAGGER_ENABLED` | 是否启用 swagger-ui（生产可 false 整体关闭；认证启用后访问本就需登录） | true |

策略与股票池：`stock-server/src/main/resources/config/strategies.yaml`（"策略类型 + 参数 + 命名过滤器"，可用 `--stock.strategies-config=file:...` 覆盖为外部文件）。

## 部署

见 `deploy/README.md`：2C4G VPS + Caddy 自动 HTTPS + systemd + cron 每市场独立窗口 + mysqldump 每日备份。公网暴露前逐项过安全清单（认证、HTTPS、MySQL 不公网、备份恢复演练）。

## 已知边界（V1 明确不做）

实时/盘中信号、AGENT 新闻分析、回测、完整 K 线蜡烛图（东财链接替代）、多用户权限体系。架构上均为纯增量扩展点（`MarketDataProvider` / `Notifier` / `Strategy` SPI），详见 `docs/architecture.md`。

## License

MIT
