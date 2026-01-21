package com.hao.strategyengine.core.stream.strategy;

import com.hao.strategyengine.cache.TradeDateCache;
import dto.HistoryTrendDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略基础抽象类
 * <p>
 * 设计目的：
 * 1. 提供所有策略的公共能力（如交易日历加载）。
 * 2. 定义策略统一接口，供 StrategyDispatcher 调用。
 * 3. 子类继承后自动获得交易日历校验等能力。
 * <p>
 * 公共能力：
 * - 交易日历自动加载（@PostConstruct）
 * - 交易日校验方法
 * - 统一的策略匹配接口 isMatch()
 *
 * @author hli
 * @date 2026-01-21
 */
@Slf4j
public abstract class BaseStrategy {

    @Autowired
    protected TradeDateCache tradeDateCache;

    /**
     * 交易日历列表（从缓存自动加载）
     */
    protected List<LocalDate> tradeDateList;

    /**
     * 获取策略唯一标识
     *
     * @return 策略ID，如 "NINE_TURN_RED"
     */
    public abstract String getId();

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
     * 信号触发后的回调（可选覆盖）
     * <p>
     * 子类可覆盖此方法以实现自定义的信号处理逻辑，如：
     * - 记录信号到数据库
     * - 发送通知
     * - 触发下单操作
     *
     * @param dto 触发信号的行情数据
     */
    public void onSignalTriggered(HistoryTrendDTO dto) {
        log.info("{}信号触发|Signal_triggered,code={},date={},price={}",
                getId(), dto.getWindCode(), dto.getTradeDate(), dto.getLatestPrice());
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

