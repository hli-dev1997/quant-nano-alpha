-- ============================================================
-- 信号中心数据库表结构
-- 数据库：quant_db
-- ============================================================

-- 股票信号表
-- 存储策略触发的信号数据，支持风控过滤和历史回测
CREATE TABLE IF NOT EXISTS `quant_stock_signal` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `wind_code` VARCHAR(20) NOT NULL COMMENT '股票代码，如：000001.SZ',
    `stock_name` VARCHAR(50) DEFAULT NULL COMMENT '股票名称',
    `strategy_name` VARCHAR(50) NOT NULL COMMENT '策略名称，如：RED_NINE_TURN',
    `signal_type` VARCHAR(10) NOT NULL COMMENT '信号类型：BUY-买入, SELL-卖出',
    `trigger_price` DECIMAL(12, 4) DEFAULT NULL COMMENT '触发价格',
    `signal_time` DATETIME DEFAULT NULL COMMENT '信号触发时间（精确到秒）',
    `trade_date` DATE NOT NULL COMMENT '交易日',
    `show_status` TINYINT NOT NULL DEFAULT 0 COMMENT '展示状态：1-通过, 0-拦截, -1-中性',
    `risk_snapshot` INT DEFAULT NULL COMMENT '风控分数快照（0-100）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 幂等唯一索引：同一股票+策略+交易日只有一条记录
    UNIQUE KEY `uk_code_strategy_date` (`wind_code`, `strategy_name`, `trade_date`),
    -- 查询索引：按策略和交易日查询通过的信号
    KEY `idx_strategy_date_status` (`strategy_name`, `trade_date`, `show_status`),
    -- 查询索引：按交易日查询所有通过的信号
    KEY `idx_trade_date_status` (`trade_date`, `show_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票策略信号表';

-- ============================================================
-- 索引设计说明：
-- 1. uk_code_strategy_date: 唯一索引，保证幂等性
--    使用 INSERT ON DUPLICATE KEY UPDATE 时触发
-- 2. idx_strategy_date_status: 按策略查询指定交易日通过的信号
--    用于刷新 Redis 缓存
-- 3. idx_trade_date_status: 按交易日查询所有通过的信号
--    用于股票列表 API 查询
-- ============================================================
