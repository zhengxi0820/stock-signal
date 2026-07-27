# 部署手册（2C4G 轻量云服务器，Ubuntu）

> 对应 docs/architecture.md「安全边界」。公网暴露前本手册所有关卡必须完成。
> 血泪教训（2026-07-27 root 被爆破，详见 docs/pitfalls.md #15）：**先焊门（第 0 步），再装任何服务。**

## 0. 装机安全前置（先于一切，顺序不可逆）

```bash
# ① 绑定运维公钥（腾讯云控制台 → SSH密钥 → 绑定实例；或 TAT 网页终端手动写入）
#    公钥追加到 /home/ubuntu/.ssh/authorized_keys

# ② 验证密钥能登录（先确认能进，再关门——顺序反了会把自己锁外面）
ssh ubuntu@<服务器IP>   # 必须成功

# ③ 关闭密码认证（焊死暴力破解的锁孔）
echo "PasswordAuthentication no
KbdInteractiveAuthentication no" | sudo tee /etc/ssh/sshd_config.d/99-key-only.conf
sudo sshd -t && sudo systemctl reload ssh

# ④ 双向验证
sudo sshd -T | grep passwordauthentication   # 必须输出: passwordauthentication no
# 另一终端用密码试登，应返回 Permission denied (publickey)

# ⑤ fail2ban 兜底（失败 5 次封 24 小时）
sudo apt install -y fail2ban
printf "[sshd]\nenabled = true\nmaxretry = 5\nbantime = 24h\nfindtime = 10m\n" | sudo tee /etc/fail2ban/jail.d/sshd-local.conf
sudo systemctl enable --now fail2ban && sudo fail2ban-client status sshd
```

## 1. 基础环境

```bash
sudo apt update && sudo apt install -y openjdk-17-jre-headless mysql-server python3 python3-venv

# MySQL 初始化
sudo mysql < docs/db/schema.sql
sudo mysql -e "CREATE USER IF NOT EXISTS 'stock'@'localhost' IDENTIFIED BY '改为强密码'; \
  GRANT ALL PRIVILEGES ON stock_signal.* TO 'stock'@'localhost'; FLUSH PRIVILEGES;"
# MySQL 仅监听本机（确认 /etc/mysql/mysql.conf.d/mysqld.cnf 中 bind-address=127.0.0.1）

# Python 取数环境（国内服务器用清华镜像提速）
cd /opt/stock-signal/fetch && python3 -m venv .venv && .venv/bin/pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
```

Caddy 安装（Ubuntu 默认源没有，走官方仓库）：

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -s1L "https://dl.cloudsmith.io/public/caddy/stable/gpg.key" | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -s1L "https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt" | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

## 2. 应用部署

```bash
sudo mkdir -p /opt/stock-signal/backup && sudo chown -R $USER /opt/stock-signal
# 本机构建并上传：mvn -DskipTests install 后，
# scp stock-server/target/stock-server-*.jar、fetch/、deploy/、docs/db/schema.sql 到 /opt/stock-signal/

# 环境变量（/opt/stock-signal/env.sh，chmod 600，勿入仓）
# 复制 deploy/env.sh.example 填入真实值。注意：行首禁止 export ——
# 该文件同时被 systemd EnvironmentFile 读取（不识别 export，会整行忽略，实机踩过）和 shell source 使用。

# systemd 服务
sudo cp deploy/stock-signal.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now stock-signal
curl http://127.0.0.1:8080/api/health   # 应返回 {"status":"UP"}
```

## 3. 反向代理（Caddy）

**有域名（HTTPS）**：`/etc/caddy/Caddyfile` 写入（完整含安全头示例见 deploy/Caddyfile）：

```
stock.example.com, www.stock.example.com {
    reverse_proxy 127.0.0.1:8080
    encode gzip
    # header 安全块照抄 deploy/Caddyfile
}
```

`sudo systemctl reload caddy` 后自动签发证书。同时 `SECURE_COOKIE=true` 并重启应用。

**无域名/域名审核中（HTTP 过渡）**：站点块写 `:80 { reverse_proxy 127.0.0.1:8080 ... }`，同时 `SECURE_COOKIE=false`（否则 HTTP 下 Cookie 不下发，登录不上）。域名生效后切回 HTTPS + `SECURE_COOKIE=true`。

## 4. 初始数据 + 定时任务

```bash
# 初始数据灌库（约 20-40 分钟，带限流容错）
cd /opt/stock-signal/fetch && set -a && . /opt/stock-signal/env.sh && set +a
.venv/bin/python fetch_index.py --index CSI300
.venv/bin/python fetch_batch.py --index CSI300 --interval 2.5

# 触发首次扫描验证四阶段（SH/SZ 各一次）
curl -X POST "http://127.0.0.1:8080/api/daily-runs?market=SH"   # 需先登录拿 Cookie（AUTH_TOKEN 已配时）
```

```cron
# crontab -e（见 deploy/crontab.example）
30 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=SH >> /var/log/stock-cron.log 2>&1
45 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=SZ >> /var/log/stock-cron.log 2>&1
50 17 * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=HK >> /var/log/stock-cron.log 2>&1
0  8  * * *  curl -s -X POST http://127.0.0.1:8080/api/daily-runs?market=US >> /var/log/stock-cron.log 2>&1
15 3  * * *  /opt/stock-signal/deploy/backup.sh >> /var/log/stock-backup.log 2>&1
```

## 5. 安全与运维检查单（上线前逐项打勾）

**装机层**
- [ ] `sudo sshd -T | grep passwordauthentication` 输出 `passwordauthentication no`
- [ ] 密码登录被拒绝（`Permission denied (publickey)`），密钥登录正常
- [ ] `sudo fail2ban-client status sshd` 正常显示 jail

**应用层**
- [ ] AUTH_TOKEN 已配置（页面登录生效，股票池 CRUD 需登录）
- [ ] 登录防爆破已验证：连续 5 次错误口令 → 429 锁定 15 分钟
- [ ] HTTPS 可访问（或 HTTP 过渡已记录待办）；Cookie 属性正确（HTTPS 时带 Secure + SameSite=Strict）
- [ ] 安全响应头已生效：`curl -sI https://域名 | grep -i x-frame`
- [ ] Swagger 已收口：`curl https://域名/api-docs` 未登录返回 401
- [ ] MySQL 仅 127.0.0.1（`ss -tlnp | grep 3306`）
- [ ] env.sh 权限 600，不含在 git 中

**运维层**
- [ ] backup.sh 手动执行成功且恢复演练过一次（backup.sh 带 `--no-tablespaces`，因为 stock 账号无 PROCESS 权限）
- [ ] 连续 3 个交易日：job_run 四阶段 SUCCESS、微信推送到达
- [ ] 父亲手机实测：打开网址 → 登录 → 看金叉大屏 → 管理自选

## 6. fail2ban 对接应用登录日志（可选增强）

应用审计日志格式为 `AUTH login fail ip=...`，fail2ban 规则示例：

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
