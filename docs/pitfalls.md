# 踩坑记录（Pitfalls Runbook）

> 本项目实战中踩过的坑，每条含现象、根因、修法。新会话遇到似曾相识的报错先来这查。
> 更新纪律：新踩坑 → 先修 → 再补录本表 → 涉及行为变化的同步 `docs/architecture.md`。

| # | 坑 | 现象 | 根因 | 修法 |
|---|---|---|---|---|
| 1 | 东财反爬限流 | 连续抓取后连接重置（几小时后自解，且 curl 能连、Python requests 不能连） | IP 级反爬 + TLS 客户端指纹识别 | A股三源切换（东财/新浪/腾讯）+ 健康记忆（东财连败2次后备源优先）+ 间隔2.5s + 指数退避 + 断点续灌 |
| 2 | 系统代理干扰抓取 | curl 能连，Python 抓不了 | requests 自动读系统代理 | 脚本顶部 `os.environ["NO_PROXY"]="*"` 强制直连 |
| 3 | akshare 新浪港美接口失效 | `stock_hk_daily`/`stock_us_daily` 解析报错 | 上游新浪改了响应格式，akshare 未适配 | 港美股改走东财接口（`stock_hk_hist`/`stock_us_hist`，美股代码经现货列表映射 secid） |
| 4 | systemd 不认 export | 应用启动报 `Failed to obtain JDBC Connection` | `EnvironmentFile` 把 `export KEY=V` 整行忽略 | env.sh 一律裸 `KEY=VALUE`（shell 侧用 `set -a` 导出） |
| 5 | CSP 拦内联脚本 | 页面登录点击无任何请求发出 | `script-src` 未含 unsafe-inline，内联 `<script>` 全被拦 | 内联脚本抽到外部 `app.js` |
| 6 | CSP 拦 eval | 修完 #5 后白屏，console 报 blocks 'eval' | Vue 运行时模板编译器用 `new Function()` | `script-src 'self' 'unsafe-eval'`；教训：安全头必须对应用实际形态配并在真实浏览器验证 |
| 7 | Vue3 Proxy 包 ECharts 实例 | 图表渲染正常但悬浮提示完全不显示 | ECharts 实例放 `data()` 被 Vue3 包成响应式 Proxy | 实例存非响应式属性（`this._chart`） |
| 8 | signal 是 MySQL 保留字 | 建表报语法错误 | `SIGNAL` 为 MySQL 保留关键字 | 表名用 `signals` |
| 9 | /api-docs 绕过认证过滤器 | Swagger 未登录可访问 | `/api-docs` 前缀是连字符，不匹配 `startsWith("/api/")` | 路径判断显式列出 `api-docs`/`swagger-ui` 前缀 |
| 10 | PushPlus 失败也返回 HTTP 200 | 日志显示"推送成功"但微信收不到 | 业务错误码在响应体 `code` 字段（如未实名 905） | `WechatNotifier` 同时校验 body 的 `"code":200` |
| 11 | mysqldump 权限不足 | `PROCESS privilege` 报错 | 应用账号无 PROCESS 权限 | 加 `--no-tablespaces`（不额外授权，最小权限原则） |
| 12 | Windows 下 jar 文件锁 | `mvn package` repackage 失败 | 运行中的 java 进程锁住 target jar | 重新打包前先停掉运行中的应用 |
| 13 | pkill 误杀自身 | ssh 远程执行 pkill 后连接异常断开（exit 255） | `pkill -f fetch_batch.py` 匹配到自身所在的 bash -c 命令行 | 模式写成 `[f]etch_batch.py`（括号字符类不匹配自身） |
| 14 | 批处理并发漏洞 | 上次运行未结束时又触发，两个抓取进程并发 | 10 分钟冷却只看时间不看状态 | 触发前检查 RUNNING 阶段；应用启动时把残留 RUNNING 标记 FAILED（`StaleRunCleanup`） |
| 15 | root 密码被爆破（2026-07-27） | 腾讯云告警 1630 次尝试，德国 IP 成功登录 root，翻读 env.sh | 控制台绑 SSH 密钥时未关闭密码认证 + root 弱密码 | 应急处置：关密码认证（仅密钥）+ fail2ban（5 次封 24h）+ 全部密码/token 轮换 + 重装系统。教训：公网机器"密码登录不存在"才是防线 |

## 已固化为架构决策的教训（详见 docs/architecture.md）

- 免费数据源限流是常态不是异常 → 抓取层容错是设计的一部分（#1/#2/#3）。
- 批处理必然重跑 → 幂等与可追溯是第一性原理（signals 唯一约束、job_run、#14）。
- 公网部署默认敌意环境 → 认证/限流/Cookie 三件套/CSP/MySQL 不公网/fail2ban（#5/#6/#9/#15）。
