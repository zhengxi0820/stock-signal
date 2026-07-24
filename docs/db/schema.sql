-- stock_signal 数据库初始化脚本
-- 与 docs/architecture.md 的表设计保持一致；表结构变更必须同步更新该文档。

CREATE DATABASE IF NOT EXISTS stock_signal
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE stock_signal;

-- 日线行情（前复权）。成交量单位：股；价格为对应市场货币。
CREATE TABLE IF NOT EXISTS daily_quote (
    market      VARCHAR(8)    NOT NULL COMMENT '市场: SH/SZ/HK/US',
    code        VARCHAR(16)   NOT NULL COMMENT '代码: 600519 / 00700 / AAPL',
    trade_date  DATE          NOT NULL COMMENT '交易日（该市场本地日历）',
    open        DECIMAL(12,4) NOT NULL,
    high        DECIMAL(12,4) NOT NULL,
    low         DECIMAL(12,4) NOT NULL,
    close       DECIMAL(12,4) NOT NULL,
    volume      BIGINT        NOT NULL COMMENT '成交量（股）',
    adjust      VARCHAR(8)    NOT NULL DEFAULT 'qfq' COMMENT '复权口径，统一 qfq 前复权',
    source      VARCHAR(32)   NOT NULL COMMENT '数据源，如 akshare-eastmoney',
    fetched_at  DATETIME      NOT NULL COMMENT '抓取时间',
    PRIMARY KEY (market, code, trade_date)
) ENGINE=InnoDB COMMENT='日线 OHLCV（前复权）';

-- 指数成分（带快照日期，可复现某日成分）
CREATE TABLE IF NOT EXISTS index_constituents (
    index_code   VARCHAR(16) NOT NULL COMMENT '指数代码，如 CSI300',
    market       VARCHAR(8)  NOT NULL,
    code         VARCHAR(16) NOT NULL,
    snapshot_date DATE       NOT NULL COMMENT '成分快照日期',
    PRIMARY KEY (index_code, market, code, snapshot_date)
) ENGINE=InnoDB COMMENT='指数成分快照';

-- 股票池。"index:CSI300" 形式的引用存于 YAML 配置，展开后落本表或直接查询时展开（V1 查询时展开）
CREATE TABLE IF NOT EXISTS stock_pool (
    pool_name VARCHAR(32) NOT NULL,
    market    VARCHAR(8)  NOT NULL,
    code      VARCHAR(16) NOT NULL,
    PRIMARY KEY (pool_name, market, code)
) ENGINE=InnoDB COMMENT='股票池';

-- 信号。重跑幂等：唯一约束覆盖策略版本
-- 表名为 signals（signal 是 MySQL 保留字）
CREATE TABLE IF NOT EXISTS signals (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    market             VARCHAR(8)   NOT NULL,
    code               VARCHAR(16)  NOT NULL,
    trade_date         DATE         NOT NULL COMMENT '信号对应的交易日',
    strategy           VARCHAR(32)  NOT NULL COMMENT '策略名，如 kdj-cross',
    strategy_version   VARCHAR(32)  NOT NULL COMMENT '策略参数版本，改参即变，历史信号可解释',
    signal_type        VARCHAR(16)  NOT NULL COMMENT 'GOLDEN_CROSS / DEATH_CROSS',
    indicator_snapshot JSON         NOT NULL COMMENT '触发时指标值快照，如 {"K":23.4,"D":21.8,"J":26.6}',
    run_id             VARCHAR(36)  NOT NULL COMMENT '产生该信号的 DailyRun',
    generated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_signal_idem (market, code, trade_date, strategy, strategy_version, signal_type),
    KEY idx_signal_date (trade_date),
    KEY idx_signal_stock (market, code)
) ENGINE=InnoDB COMMENT='策略信号';

-- 运维表：每次运行的各阶段记录（成功/失败/新鲜度/告警去重依据）
CREATE TABLE IF NOT EXISTS job_run (
    run_id      VARCHAR(36) NOT NULL,
    market      VARCHAR(8)  NOT NULL,
    phase       VARCHAR(16) NOT NULL COMMENT 'FETCH/VALIDATE/EVALUATE/NOTIFY',
    status      VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED/SKIPPED',
    started_at  DATETIME    NOT NULL,
    finished_at DATETIME    NULL,
    stats       JSON        NULL COMMENT '阶段统计，如 {"fetched":300,"expected":300}',
    error       TEXT        NULL,
    PRIMARY KEY (run_id, market, phase),
    KEY idx_job_run_market (market, started_at)
) ENGINE=InnoDB COMMENT='DailyRun 各阶段运行记录';
