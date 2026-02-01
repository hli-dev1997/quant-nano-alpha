package com.hao.strategyengine.core.stream.strategy;

import com.hao.strategyengine.cache.TradeDateCache;
import com.hao.strategyengine.integration.kafka.StrategySignalProducer;
import dto.HistoryTrendDTO;
import dto.StrategySignalDTO;
import enums.strategy.SignalTypeEnum;
import enums.strategy.StrategyRiskLevelEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 策略基础抽象类 (Base Strategy)
 * <p>
 * 设计目的：
 * 1. 提供所有策略的公共能力（如交易日历加载、信号发送）。
 * 2. 定义策略统一接口，供 StrategyDispatcher 调用。
 * 3. 子类继承后自动获得交易日历校验、Kafka 信号发送等能力。
 * <p>
 * 公共能力：
 * - 交易日历自动加载（@PostConstruct）
 * - 交易日校验方法
 * - 统一的策略匹配接口 isMatch()
 * - 信号触发后自动发送 Kafka 消息
 *
 * @author hli
 * @date 2026-01-21
 */
@Slf4j
public abstract class BaseStrategy {

    @Autowired
    protected TradeDateCache tradeDateCache;

    @Autowired
    protected StrategySignalProducer strategySignalProducer;

    /**
     * 交易日历列表（从缓存自动加载）
     */
    protected List<LocalDate> tradeDateList;

    /**
     * 日期格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取策略唯一标识
     *
     * @return 策略ID，如 "NINE_TURN_RED"
     */
    public abstract String getId();

    /**
     * 获取策略的信号类型（买入/卖出）
     * <p>
     * 子类必须实现，用于构建 StrategySignalDTO。
     *
     * @return 信号类型枚举
     */
    public abstract SignalTypeEnum getSignalType();

    /**
     * 获取策略的风险等级
     * <p>
     * 子类可覆盖以返回不同的风险等级，默认为中风险。
     * 风险等级用于风控降级时的差异化处理。
     *
     * @return 风险等级枚举
     */
    public StrategyRiskLevelEnum getRiskLevel() {
        return StrategyRiskLevelEnum.MEDIUM;
    }

    /**
     * 判断是否触发策略信号（统一入口）
     * <p>
     * 由子类实现具体的策略判断逻辑。
     * StrategyDispatcher 通过此方法分发数据给各策略。
     *
     * @param dto 分时行情数据
     * @return true-触发信号，false-未触发
     */
    public abstract boolean isMatch(HistoryTrendDTO dto);

    /**
     * 信号触发后的回调
     * <p>
     * 当 isMatch() 返回 true 时，由 StrategyDispatcher 调用此方法。
     * 默认行为：
     * 1. 记录日志
     * 2. 构建 StrategySignalDTO
     * 3. 发送到 Kafka
     * <p>
     * 子类可覆盖此方法以添加自定义逻辑。
     *
     * @param dto 触发信号的行情数据
     */
    public void onSignalTriggered(HistoryTrendDTO dto) {
        // [FULL_CHAIN_STEP_08] 策略信号触发 - isMatch() 返回 true 后执行
        log.info("{}信号触发|Signal_triggered,code={},date={},price={}",
                getId(), dto.getWindCode(), dto.getTradeDate(), dto.getLatestPrice());

        // [FULL_CHAIN_STEP_09] 构建信号 DTO 并发送到 Kafka → 信号中心消费
        // @see docs/architecture/FullChainDataFlow.md
        StrategySignalDTO signal = buildSignalDTO(dto);
        strategySignalProducer.sendSignal(signal);
    }

    /**
     * 构建策略信号 DTO
     * <p>
     * 将行情数据和策略元信息封装为 DTO，用于 Kafka 传输。
     *
     * @param dto 行情数据
     * @return 策略信号 DTO
     */
    protected StrategySignalDTO buildSignalDTO(HistoryTrendDTO dto) {
        // signalTime 必须非空（数据库 NOT NULL 约束）
        LocalDateTime signalTime = dto.getTradeDate();
        if (signalTime == null) {
            signalTime = LocalDateTime.now();
        }
        String tradeDate = signalTime.toLocalDate().format(DATE_FORMATTER);

        return StrategySignalDTO.builder()
                .windCode(dto.getWindCode())
                .strategyId(getId())
                .signalType(getSignalType().getCode())
                .signalTime(signalTime)
                .triggerPrice(dto.getLatestPrice())
                .riskLevel(getRiskLevel().getCode())
                .tradeDate(tradeDate)
                .traceId(dto.getTraceId())  // 透传 traceId 用于全链路追踪
                .build();
    }

    /**
     * 初始化交易日历
     * <p>
     * 从 TradeDateCache 加载全量交易日历（2020年至今），在 Spring Bean 初始化后自动执行。
     * <p>
     * 子类可覆盖此方法以实现自定义初始化逻辑。
     */
    @PostConstruct
    public void initTradeDateList() {
        this.tradeDateList = tradeDateCache.getAllTradeDates();
        log.info("策略交易日历初始化完成|Strategy_trade_date_init,strategy={},dateCount={}",
                getId(), tradeDateList != null ? tradeDateList.size() : 0);
    }

    /**
     * 获取交易日历列表
     *
     * @return 交易日历列表
     */
    public List<LocalDate> getTradeDateList() {
        return tradeDateList;
    }

    /**
     * 判断指定日期是否为交易日
     *
     * @param date 日期
     * @return true-是交易日
     */
    protected boolean isTradingDay(LocalDate date) {
        return tradeDateList != null && tradeDateList.contains(date);
    }
}
