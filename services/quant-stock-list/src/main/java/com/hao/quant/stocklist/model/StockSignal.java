package com.hao.quant.stocklist.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票信号实体（只读，用于查询）
 * <p>
 * 类职责：
 * 用于 L3 MySQL 查询的信号实体类，与 Signal Center 的 StockSignal 字段保持一致。
 * <p>
 * 使用场景：
 * MultiLevelCacheService 从 MySQL 查询信号数据时使用。
 *
 * @author hli
 * @date 2026-02-01
 */
@Data
public class StockSignal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 股票代码
     * 格式如：000001.SZ、600519.SH
     */
    private String windCode;

    /**
     * 策略ID
     * 对应 StrategyMetaEnum.id，如：NINE_TURN_RED、MA_BULLISH
     */
    private String strategyId;

    /**
     * 信号类型
     * BUY-买入 / SELL-卖出
     */
    private String signalType;

    /**
     * 触发价格
     */
    private Double triggerPrice;

    /**
     * 信号触发时间
     */
    private LocalDateTime signalTime;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 展示状态
     * 1-通过 / 0-拦截 / -1-中性
     */
    private Integer showStatus;

    /**
     * 风控分数快照
     * 记录信号产生时的市场情绪分数
     */
    private Integer riskSnapshot;

    /**
     * 记录状态
     * 0-无效 / 1-有效
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
