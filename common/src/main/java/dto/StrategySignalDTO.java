package dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 策略信号数据传输对象 (Strategy Signal DTO)
 * <p>
 * 类职责：
 * 用于策略引擎 -> Kafka -> 信号中心的消息传递，封装策略触发的信号数据。
 * <p>
 * 使用场景：
 * 1. 策略引擎触发信号后，封装为 DTO 发送到 Kafka
 * 2. 信号中心消费 Kafka 消息时，反序列化为 DTO
 * 3. 落库前转换为实体对象
 * <p>
 * 设计目的：
 * 1. 解耦策略引擎和信号中心，通过 Kafka 异步传输
 * 2. 保留完整的信号上下文信息，支持后续回测分析
 * 3. 携带风险等级信息，支持降级时的差异化处理
 *
 * @author hli
 * @date 2026-01-30
 */
public class StrategySignalDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 股票代码
     * 格式如：000001.SZ（深圳）、600519.SH（上海）
     */
    private String windCode;

    /**
     * 策略ID
     * 对应 StrategyMetaEnum.id
     * 如：NINE_TURN_RED、NINE_TURN_GREEN、MA_BULLISH
     */
    private String strategyId;

    /**
     * 信号类型
     * BUY - 买入信号
     * SELL - 卖出信号
     */
    private String signalType;

    /**
     * 信号触发时间
     * 精确到秒的信号产生时刻
     */
    private LocalDateTime signalTime;

    /**
     * 触发价格
     * 信号触发时的股票价格
     */
    private Double triggerPrice;

    /**
     * 策略风险等级
     * 对应 StrategyRiskLevelEnum
     * LOW - 低风险（周线策略），降级时可放行
     * MEDIUM - 中风险
     * HIGH - 高风险（打板策略），降级时强制拦截
     */
    private String riskLevel;

    /**
     * 交易日
     * 信号所属的交易日期（格式：yyyy-MM-dd）
     */
    private String tradeDate;

    /**
     * 链路追踪ID
     * <p>
     * 格式: yyyyMMdd_HHmmss（如: 20260101_093000）
     * 从行情DTO中透传，用于全链路日志追踪
     *
     * @see docs/architecture/FullChainDataFlow.md
     */
    private String traceId;

    /**
     * 默认构造函数
     */
    public StrategySignalDTO() {
    }

    // ==================== Getter & Setter ====================

    public String getWindCode() {
        return windCode;
    }

    public void setWindCode(String windCode) {
        this.windCode = windCode;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public LocalDateTime getSignalTime() {
        return signalTime;
    }

    public void setSignalTime(LocalDateTime signalTime) {
        this.signalTime = signalTime;
    }

    public Double getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(Double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(String tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String toString() {
        return "StrategySignalDTO{" +
                "windCode='" + windCode + '\'' +
                ", strategyId='" + strategyId + '\'' +
                ", signalType='" + signalType + '\'' +
                ", signalTime=" + signalTime +
                ", triggerPrice=" + triggerPrice +
                ", riskLevel='" + riskLevel + '\'' +
                ", tradeDate='" + tradeDate + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }

    // ==================== Builder Pattern ====================

    /**
     * Builder 静态内部类
     * 提供链式调用构建 DTO 对象
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final StrategySignalDTO dto = new StrategySignalDTO();

        public Builder windCode(String windCode) {
            dto.setWindCode(windCode);
            return this;
        }

        public Builder strategyId(String strategyId) {
            dto.setStrategyId(strategyId);
            return this;
        }

        public Builder signalType(String signalType) {
            dto.setSignalType(signalType);
            return this;
        }

        public Builder signalTime(LocalDateTime signalTime) {
            dto.setSignalTime(signalTime);
            return this;
        }

        public Builder triggerPrice(Double triggerPrice) {
            dto.setTriggerPrice(triggerPrice);
            return this;
        }

        public Builder riskLevel(String riskLevel) {
            dto.setRiskLevel(riskLevel);
            return this;
        }

        public Builder tradeDate(String tradeDate) {
            dto.setTradeDate(tradeDate);
            return this;
        }

        public Builder traceId(String traceId) {
            dto.setTraceId(traceId);
            return this;
        }

        public StrategySignalDTO build() {
            return dto;
        }
    }
}
