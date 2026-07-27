# 交接与进度（Handoff）

> 本文档是新会话/新协作者的接手入口。**阅读顺序建议：`AGENTS.md` → `docs/architecture.md` → 本文件 → `docs/pitfalls.md`。**
> 更新纪律：每次里程碑推进或状态变化后同步更新本文件（绝对日期）。

## 当前状态快照（2026-07-27）

- 系统已上线：页面 + 每日自动扫描 + 微信推送全链路在生产运行过（2026-07-25 起）。
- **2026-07-27 服务器 root 密码被暴力破解入侵一次**（德国 IP，翻读 env.sh 后离开，未植入后门，详见 `docs/pitfalls.md` #15）。已完成应急处置（密钥认证、fail2ban、密码/口令/库密码轮换）。
- **用户决定重装服务器操作系统**。接手时服务器可能是空机，第一步就是按 `deploy/README.md` 重新部署。

## 已验证可用的能力（V1 全部里程碑完成）

- 沪深300 全量日线（300 只，~21.6 万行）每日自动增量抓取（东财/新浪/腾讯三源容错）。
- DailyRun 四阶段编排（FETCH→VALIDATE→EVALUATE→NOTIFY），幂等可重跑，job_run 可追溯。
- KDJ 金叉死叉信号（中式口径，与东财对齐），signals 表全历史回填（~4400 条）。
- 金叉大屏页面（当前状态/今日信号/指标图/历史查询/股票池管理/运行状态条）。
- 微信 PushPlus 推送（摘要+失败告警），单账号认证（限流+审计），HTTPS（Caddy 自动证书）。
- 每日 mysqldump 备份、cron 每市场调度、systemd 自启。

## 待办（按优先级）

1. **重装后重新部署**（最高优先）：`deploy/README.md` 全流程。新增硬性前置：**装系统后第一件事关闭 SSH 密码认证 + 装 fail2ban，再开任何服务**。数据无需迁移——fetch 脚本可全量重建行情，signals 由引擎幂等回填。
2. **PushPlus token 轮换**：旧 token 已在入侵中泄露，需用户在 pushplus.plus 后台重新生成后更新服务器 `env.sh`（在拿到新 token 前微信推送暂停）。
3. **域名恢复**：zhengxi.online 审核通过并重新解析到服务器 IP 后，Caddy 自动签证书，然后 `SECURE_COOKIE=true` 并重启。（当前为 HTTP + IP 直连过渡，`SECURE_COOKIE=false`。）
4. **港/美股数据源实机验证**：代码已就绪（`fetch_daily.py --market HK --code 00700` / `--market US --code AAPL`），服务器 IP 未被东财封禁，跑通即接入 cron（已配 17:50 HK / 08:00 US）。
5. **连续 3 个交易日观察**：cron 自动运行、推送到达、页面"今日新信号"更新。
6. V2 候选（未排期）：AGENT 新闻分析（Spring AI/LangChain4j，架构扩展点已留）、周/月线策略、回测（signals 表已预留）。

## 关键事实（无秘密）

- 仓库：github.com/zhengxi0820/stock-signal（main 分支，全部最新）。
- 服务器：腾讯云轻量 43.138.158.123（广州），Ubuntu 22.04，用户 `ubuntu`（sudo），SSH 仅密钥。
- 应用路径：`/opt/stock-signal/`（jar、env.sh、fetch/、backup/、deploy/）。
- **所有凭据（DB 密码、AUTH_TOKEN、PushPlus token）只存于服务器 `/opt/stock-signal/env.sh`（chmod 600），严禁写入仓库或任何文档。**
- 数据库：MySQL 8 仅监听 127.0.0.1，应用账号 `stock`（仅 stock_signal.* 权限）。
- 触发扫描：`curl -X POST "http://127.0.0.1:8080/api/daily-runs?market=SH"`（10 分钟冷却 + 并发保护）。

## 里程碑历史（一句话版）

M0 骨架 → M1a 单票数据管道 → M2a KDJ 引擎（13 单测）→ M3a 端到端幂等 → M4 API+页面 → M1b/M3b 沪深300 扩容 → M5 微信推送 → M6 认证+部署资产 → M7 港美代码+开源准备 → 2026-07-25 上线 → 2026-07-27 入侵处置+决定重装。
