package com.hao.signalcenter.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票信号实体类 (Stock Signal Entity)
 * <p>
 * 对应数据库表：quant_stock_signal
 * <p>
 * 设计目的：
 * 持久化策略触发的信号数据，支持：
 * 1. 股票列表查询展示
 * 2. 历史信号回测分析
 * 3. 风控规则验证
 *
 * @author hli
 * @date 2026-01-30
 */
public class StockSignal implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 股票代码
     * 格式如：000001.SZ
     */
    private String windCode;

    /**
     * 股票名称
     */
    private String stockName;

    /**
     * 策略名称
     * 如：RED_NINE_TURN、BULLISH_MA
     */
    private String strategyName;

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
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // ==================== Getter & Setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWindCode() {
        return windCode;
    }

    public void setWindCode(String windCode) {
        this.windCode = windCode;
    }

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public Double getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(Double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public LocalDateTime getSignalTime() {
        return signalTime;
    }

    public void setSignalTime(LocalDateTime signalTime) {
        this.signalTime = signalTime;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public Integer getShowStatus() {
        return showStatus;
    }

    public void setShowStatus(Integer showStatus) {
        this.showStatus = showStatus;
    }

    public Integer getRiskSnapshot() {
        return riskSnapshot;
    }

    public void setRiskSnapshot(Integer riskSnapshot) {
        this.riskSnapshot = riskSnapshot;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "StockSignal{" +
                "id=" + id +
                ", windCode='" + windCode + '\'' +
                ", stockName='" + stockName + '\'' +
                ", strategyName='" + strategyName + '\'' +
                ", signalType='" + signalType + '\'' +
                ", triggerPrice=" + triggerPrice +
                ", signalTime=" + signalTime +
                ", tradeDate=" + tradeDate +
                ", showStatus=" + showStatus +
                ", riskSnapshot=" + riskSnapshot +
                '}';
    }
}
