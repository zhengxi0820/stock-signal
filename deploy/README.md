# 部署手册（M6：2C4G 轻量云服务器，Ubuntu）

> 对应 docs/architecture.md「安全边界」。公网暴露前本手册所有关卡必须完成。

## 0. 前置

- 一台 2C4G VPS（Ubuntu 22.04+），一个域名（Caddy 自动 HTTPS 需要）
- 本地已能跑通全部功能（M0~M5）

## 1. 基础环境

```bash
sudo apt update && sudo apt install -y openjdk-17-jre-headless mysql-server python3 python3-venv caddy

# MySQL 初始化
sudo mysql < docs/db/schema.sql
sudo mysql -e "CREATE USER IF NOT EXISTS 'stock'@'localhost' IDENTIFIED BY '改为强密码'; \
  GRANT ALL PRIVILEGES ON stock_signal.* TO 'stock'@'localhost'; FLUSH PRIVILEGES;"
# MySQL 仅监听本机（默认即 127.0.0.1，确认 /etc/mysql/mysql.conf.d/mysqld.cnf 中 bind-address=127.0.0.1）

# Python 取数环境
cd /opt/stock-signal/fetch && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

## 2. 应用部署

```bash
sudo mkdir -p /opt/stock-signal && sudo chown $USER /opt/stock-signal
# 上传 jar 与 fetch/ 到 /opt/stock-signal/（scp 或 git pull + mvn package）

# 环境变量（/opt/stock-signal/env.sh，chmod 600，勿入仓）
# 直接复制 deploy/env.sh.example 修改。注意：行首禁止 export ——
# 该文件同时被 systemd EnvironmentFile 读取（不识别 export，会整行忽略）和 shell source 使用

# systemd 服务：sudo cp deploy/stock-signal.service /etc/systemd/system/ && sudo systemctl enable --now stock-signal
```

## 3. HTTPS + 反向代理（Caddy）

```
# /etc/caddy/Caddyfile 加入（见 deploy/Caddyfile 示例）：
stock.example.com {
    reverse_proxy 127.0.0.1:8080
}
sudo systemctl reload caddy   # 自动签发证书
```

## 4. 定时任务（每市场独立窗口，引擎不含调度概念）

```cron
# crontab -e（见 deploy/crontab.example）
30 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=SH >> /var/log/stock-cron.log 2>&1
45 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=SZ >> /var/log/stock-cron.log 2>&1
50 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=HK >> /var/log/stock-cron.log 2>&1
0  8  * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=US >> /var/log/stock-cron.log 2>&1
15 3  * * *  /opt/stock-signal/deploy/backup.sh
```

## 5. 安全与运维检查单（上线前逐项打勾）

- [ ] AUTH_TOKEN 已配置（页面登录生效，股票池 CRUD 需登录）
- [ ] 登录防爆破已验证：连续 5 次错误口令 → 429 锁定 15 分钟（应用内置，无需额外组件）
- [ ] HTTPS 可访问（https://域名），HTTP 自动跳转；Cookie 带 Secure + SameSite=Strict
- [ ] 安全响应头已生效（deploy/Caddyfile 的 header 块）：`curl -sI https://域名 | grep -i x-frame`
- [ ] Swagger 已收口：`curl https://域名/api-docs` 未登录返回 401（或 SWAGGER_ENABLED=false 整体关闭）
- [ ] MySQL 仅 127.0.0.1（`ss -tlnp | grep 3306` 确认不公网）
- [ ] env.sh 权限 600，不含在 git 中
- [ ] backup.sh 每日执行且恢复演练过一次（`mysql stock_signal < 备份文件` 到临时库验证）
- [ ] 连续 3 个交易日：job_run 四阶段 SUCCESS、微信推送到达
- [ ] 父亲手机实测：打开网址 → 登录 → 看金叉大屏 → 管理自选

## 6. fail2ban（可选增强，封禁爆破 IP）

应用审计日志格式为 `AUTH login fail ip=...`（在 stock-signal 日志中），fail2ban 规则示例：

```ini
# /etc/fail2ban/filter.d/stock-signal.conf
[Definition]
failregex = AUTH login fail ip=<HOST>
ignoreregex =

# /etc/fail2ban/jail.d/stock-signal.conf
[stock-signal]
enabled = true
filter = stock-signal
logpath = /var/log/stock-signal.log
maxretry = 10
bantime = 1h
```

