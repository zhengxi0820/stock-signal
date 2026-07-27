# AGENTS.md — 本仓库的硬性约定

## 文档即契约（最高优先级规则）

**任何接口或行为变更，必须在同一次改动中同步维护文档，否则视为改动未完成。**

- REST API：文档由 springdoc-openapi 从代码注解生成（`/swagger-ui.html`）。新增/修改接口必须维护 `@Operation`/`@Tag` 等注解，禁止"先改代码后补文档"。
- 架构与已冻结决策：维护在 `docs/architecture.md`。凡是改动了其中记录的决策（KDJ 语义、幂等规则、DailyRun 编排、表结构等），必须同步更新该文件。
- 本文件（AGENTS.md）描述的约定若被改动，同步更新本文件。

## 构建与验证

- 构建/安装：`mvn install`（多模块项目，运行 stock-server 前必须先 install 兄弟模块）
- 启动：`mvn -pl stock-server spring-boot:run`，健康检查 `GET /api/health` 返回 `{"status":"UP"}`；页面 `http://localhost:8080`
- 触发一次扫描：`curl -X POST "http://localhost:8080/api/daily-runs?market=SH"`
- 测试：`mvn test`（stock-engine 是纯库：不依赖 Spring/DB，其单测必须可独立通过；stock-server 的集成测试在本机无 MySQL 时自动跳过）
- 环境变量（本地运行 jar 时需设置）：`FETCH_PYTHON` / `FETCH_DIR`（fetch 脚本路径）；`AUTH_TOKEN`（页面口令，本机可空，启用后本地 HTTP 需同时设 `SECURE_COOKIE=false`）；`PUSHPLUS_TOKEN`（微信推送，`NOTIFIER=wechat` 时需要）
- 注意：重新 `mvn package` 前须先停掉正在运行的 jar（Windows 文件锁会导致 repackage 失败）

## 架构红线（不可违反，除非先改 docs/architecture.md 并说明理由）

1. 信号引擎无状态、与调度解耦：引擎代码中不出现"每天/定时/batch"概念，契约是"给定 K 线序列 + 策略配置 → 信号"。
2. 业务代码只依赖 Mapper 接口，SQL 不渗入业务层。
3. 指标值不落库，落库的只有信号；`signals` 表重跑幂等（唯一约束）。注意表名是 `signals`（`signal` 是 MySQL 保留字）。
4. 数据获取隔离在 `MarketDataProvider` 接口 + fetch/ Python 薄脚本之后。
5. 密钥（webhook token、DB 密码）一律走环境变量/本地配置，严禁入仓。
6. 安全机制不可削弱：公网部署必须启用 `AUTH_TOKEN`；登录限流（5 次失败锁 15 分钟）、Swagger 认证收口、Cookie 的 Secure/SameSite 不得因"本地调试方便"而在代码中默认关闭。

## 技术栈

Java 17 + Spring Boot 3 + MyBatis + MySQL 8 + TA4J；前端为静态薄页面（本地 vendor 的 Vue/ECharts，无 npm 工程）；取数为 Python + akshare（fetch/ 目录）。

## 深入文档（按需阅读，改动相关领域时必读）

- `docs/architecture.md` — 全部冻结决策（改行为先改它）
- `docs/progress.md` — 交接与进度快照、待办清单（里程碑后更新）
- `docs/pitfalls.md` — 踩坑记录（遇到报错先查）
- `docs/db/schema.sql` — 表结构 DDL
- `deploy/README.md` — 部署手册与安全检查单
- `docs/股票信号系统_知识架构体系.md` — 面向 Java 工程师的全景知识手册（PDF 源稿）
