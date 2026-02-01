-- ============================================================
-- 信号中心数据库表结构
-- 数据库：a_share_quant
-- ============================================================
USE `a_share_quant`;
-- 股票信号流水表 (追加模式)
-- 存储策略触发的信号数据，支持历史回测和信号闪烁分析
CREATE TABLE IF NOT EXISTS `tb_quant_stock_signal` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `wind_code` VARCHAR(20) NOT NULL COMMENT '股票代码，如：000001.SZ',
    `strategy_id` VARCHAR(50) NOT NULL COMMENT '策略ID，对应StrategyMetaEnum.id，如：NINE_TURN_RED',
    `signal_type` VARCHAR(10) NOT NULL COMMENT '信号类型：BUY-买入, SELL-卖出',
    `trigger_price` DECIMAL(12, 4) DEFAULT NULL COMMENT '触发价格',
    `signal_time` DATETIME(3) NOT NULL COMMENT '信号触发时间戳（精确到毫秒），Kafka传递的实际触发时刻',
    `trade_date` DATE NOT NULL COMMENT '逻辑交易日，从signal_time衍生，用于按天分组查询',
    `show_status` TINYINT NOT NULL DEFAULT 0 COMMENT '展示状态：1-通过, 0-拦截, -1-中性',
    `risk_snapshot` INT DEFAULT NULL COMMENT '风控分数快照（0-100）',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '记录状态：0-无效, 1-有效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    -- 追加模式唯一索引：同一股票+策略+时间戳 只有一条记录（防止同一秒内并发重复）
    UNIQUE KEY `uk_code_strategy_time` (`wind_code`, `strategy_id`, `signal_time`),
    -- 查询索引：按策略和交易日查询通过的信号
    KEY `idx_strategy_date_status` (`strategy_id`, `trade_date`, `show_status`),
    -- 查询索引：按交易日查询所有通过的信号
    KEY `idx_trade_date_status` (`trade_date`, `show_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='股票策略信号流水表';

-- ============================================================
-- 设计说明：
-- 1. 追加模式 (Insert Mode)：同一天同一股票可被同一策略多次触发
-- 2. uk_code_strategy_time: 唯一索引，防止同一毫秒内并发重复插入
-- 3. signal_time: 精确到毫秒的时间戳，是区分多次触发的关键字段
-- 4. trade_date: 逻辑交易日，从signal_time衍生，用于按天分组统计
-- 5. 查询"当天最新信号"使用 ROW_NUMBER() 窗口函数
-- ============================================================

