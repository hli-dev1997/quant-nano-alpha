package com.hao.strategyengine.core.stream.strategy.impl;

import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;
import com.hao.strategyengine.core.stream.strategy.StreamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 九转序列策略（Tom DeMark's TD Sequential）
 *
 * 设计目的：
 * 1. 作为流式策略的参考实现，展示如何使用StockDomainContext进行纯内存计算。
 * 2. 实现经典的九转序列买点信号检测。
 * 3. 演示如何利用strategyProps维护策略状态（计数器）。
 *
 * 策略逻辑（九转买点）：
 * 1. 每日收盘价与4天前收盘价比较
 * 2. 若 当前价 < 4天前价格，计数器+1
 * 3. 若不满足，计数器归零
 * 4. 当计数器连续达到9时，触发买入信号
 *
 * 算法说明：
 * - 九转序列是一种逆势指标，用于捕捉超卖反弹机会
 * - 需要连续9个交易日满足条件才会触发信号
 * - 与传统均线策略互补，适合震荡行情
 *
 * 线程安全说明：
 * - 本策略通过strategyProps维护状态
 * - 由于Thread Confinement，同一股票由固定线程处理，无需同步
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Component
public class NineTurnStrategy implements StreamingStrategy {

    /**
     * 策略唯一标识
     */
    private static final String STRATEGY_ID = "NINE_TURN";

    /**
     * 计数器属性Key
     * 格式：策略ID_COUNT
     */
    private static final String COUNT_KEY = STRATEGY_ID + "_COUNT";

    /**
     * 九转所需的回溯天数
     * 与4天前价格比较，因此需要至少5天数据
     */
    private static final int LOOKBACK_DAYS = 4;

    /**
     * 触发信号的连续计数阈值
     */
    private static final int TRIGGER_COUNT = 9;

    @Override
    public String getId() {
        return STRATEGY_ID;
    }

    /**
     * 判断是否命中九转序列信号
     *
     * 实现逻辑：
     * 1. 检查历史数据是否充足（至少需要5天）
     * 2. 获取4天前的价格
     * 3. 比较当前价格与历史价格
     * 4. 更新计数器（满足条件+1，否则归零）
     * 5. 计数器达到9时返回true
     *
     * 时间复杂度：O(1)
     * 空间复杂度：O(1)
     *
     * @param tick    当前Tick数据
     * @param context 股票领域上下文
     * @return true-命中九转信号，false-未命中
     */
    @Override
    public boolean isMatch(Tick tick, StockDomainContext context) {
        // 中文：检查历史数据是否充足，九转需要至少5天数据（当天+4天回溯）
        // English: Check if enough history exists, need at least 5 days for lookback
        if (!context.hasEnoughHistory(LOOKBACK_DAYS + 1)) {
            log.debug("数据不足_跳过九转计算|Insufficient_data_skip_nine_turn,symbol={},size={}",
                    context.getSymbol(), context.getSize());
            return false;
        }

        // 中文：获取4天前的价格（O(1)复杂度）
        // English: Get price from 4 days ago (O(1) complexity)
        double priceT4 = context.getPrice(LOOKBACK_DAYS);
        double currentPrice = tick.getPrice();

        // 中文：获取当前计数器值，默认为0
        // English: Get current count, default to 0
        int currentCount = context.getStrategyPropOrDefault(COUNT_KEY, 0);

        // 中文：判断是否满足九转条件：当前价格 < 4天前价格
        // English: Check nine-turn condition: current price < price 4 days ago
        if (currentPrice < priceT4) {
            // 中文：满足条件，计数器+1
            // English: Condition met, increment counter
            currentCount++;
            context.setStrategyProp(COUNT_KEY, currentCount);

            log.debug("九转计数更新|Nine_turn_count_updated,symbol={},count={},currentPrice={},priceT4={}",
                    context.getSymbol(), currentCount, currentPrice, priceT4);

            // 中文：检查是否达到触发阈值
            // English: Check if trigger threshold reached
            if (currentCount >= TRIGGER_COUNT) {
                log.info("九转信号触发|Nine_turn_signal_triggered,symbol={},count={},price={}",
                        context.getSymbol(), currentCount, currentPrice);
                // 中文：信号触发后重置计数器，避免重复触发
                // English: Reset counter after signal to avoid duplicate triggers
                context.setStrategyProp(COUNT_KEY, 0);
                return true;
            }
        } else {
            // 中文：不满足条件，计数器归零
            // English: Condition not met, reset counter
            if (currentCount > 0) {
                log.debug("九转计数重置|Nine_turn_count_reset,symbol={},previousCount={}",
                        context.getSymbol(), currentCount);
            }
            context.setStrategyProp(COUNT_KEY, 0);
        }

        return false;
    }
}
