package com.hao.quant.stocklist.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 类说明 / Class Description:
 * 中文：股票信号视图对象，用于 API 返回。
 * English: Stock signal view object for API response.
 *
 * 设计目的 / Design Purpose:
 * 中文：与信号中心的 StockSignal 实体字段兼容，支持从 Redis 缓存反序列化。
 * English: Compatible with StockSignal entity fields from signal center, supporting deserialization from Redis cache.
 *
 * 字段说明：
 * - 基础字段：直接对应 StockSignal 实体
 * - 扩展字段：预留给股票基本面数据（如行业、市值等）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StablePicksVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 来自 StockSignal 实体的核心字段 ====================

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
     * 如：NINE_TURN_RED、MA_BULLISH
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
     */
    private Integer riskSnapshot;

    // ==================== 预留扩展字段（股票基本面数据） ====================

    /**
     * 所属行业
     * 预留字段，后续从股票基本面数据补充
     */
    private String industry;

    /**
     * 流通市值（亿元）
     * 预留字段
     */
    private Double marketCap;

    /**
     * 市盈率
     * 预留字段
     */
    private Double peRatio;

    /**
     * 综合评分
     * 预留字段，可扩展为多因子打分
     */
    private Double score;

    // ==================== 来自 StockSignal 的其他字段（用于 JSON 反序列化） ====================

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
