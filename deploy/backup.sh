#!/usr/bin/env bash
# 每日备份：mysqldump → gzip，保留 30 天。配合 cron 每日执行。
set -euo pipefail
# 加载数据库凭据（env.sh 无 export 前缀，set -a 自动导出）
set -a
. /opt/stock-signal/env.sh
set +a
BACKUP_DIR=/opt/stock-signal/backup
mkdir -p "$BACKUP_DIR"
mysqldump --no-tablespaces -u stock -p"$DB_PASSWORD" stock_signal | gzip > "$BACKUP_DIR/stock_signal-$(date +%Y%m%d).sql.gz"
find "$BACKUP_DIR" -name 'stock_signal-*.sql.gz' -mtime +30 -delete
echo "[backup] $(date +%F) done"
