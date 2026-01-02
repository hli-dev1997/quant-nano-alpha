package com.hao.strategyengine.core.stream.strategy.impl;

import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 九转序列策略单元测试
 *
 * 测试目的：
 * 1. 验证九转序列算法逻辑的正确性。
 * 2. 验证计数器的累加和重置机制。
 * 3. 验证边界条件（数据不足、信号触发后重置）。
 *
 * 设计思路：
 * - 模拟连续下跌场景，验证计数器累加
 * - 模拟中途反弹，验证计数器重置
 * - 验证九转信号触发条件
 *
 * @author hli
 * @date 2026-01-02
 */
class NineTurnStrategyTest {

    private NineTurnStrategy strategy;
    private StockDomainContext context;

    @BeforeEach
    void setUp() {
        strategy = new NineTurnStrategy();
        // 中文：使用容量20的Context，足够测试
        // English: Use context with capacity 20, enough for testing
        context = new StockDomainContext("000001.SZ", 20);
    }

    @Test
    @DisplayName("测试策略ID")
    void testGetId() {
        assertEquals("NINE_TURN", strategy.getId());
    }

    @Test
    @DisplayName("测试数据不足时返回false")
    void testInsufficientData() {
        // 中文：只有4天数据，不满足5天要求（当天+4天回溯）
        // English: Only 4 days of data, doesn't meet 5-day requirement
        for (int i = 0; i < 4; i++) {
            context.update(10.0 - i * 0.1);
        }

        Tick tick = Tick.builder()
                .symbol("000001.SZ")
                .price(9.5)
                .volume(1000)
                .eventTime(System.currentTimeMillis())
                .build();

        // 中文：数据不足，应返回false
        // English: Insufficient data, should return false
        assertFalse(strategy.isMatch(tick, context));
    }

    @Test
    @DisplayName("测试九转计数器累加")
    void testCounterIncrement() {
        // 中文：写入5天历史数据，价格递减
        // English: Write 5 days history with decreasing prices
        // Day -4: 15.0, Day -3: 14.0, Day -2: 13.0, Day -1: 12.0, Day 0: 11.0
        context.update(15.0);
        context.update(14.0);
        context.update(13.0);
        context.update(12.0);
        context.update(11.0);

        // 中文：新价格10.0 < 4天前价格15.0，满足条件
        // English: New price 10.0 < price 4 days ago 15.0, condition met
        Tick tick1 = createTick(10.0);
        // 中文：第一次满足条件，计数器=1，不触发信号
        // English: First time condition met, counter=1, no signal yet
        assertFalse(strategy.isMatch(tick1, context));
        
        // 中文：验证计数器已累加
        // English: Verify counter has been incremented
        Integer count = context.getStrategyPropOrDefault("NINE_TURN_COUNT", 0);
        assertEquals(1, count, "第一次满足条件后计数器应为1");
    }

    @Test
    @DisplayName("测试九转信号完整触发流程")
    void testNineTurnSignal() {
        // 中文：模拟完整的九转信号触发场景
        // English: Simulate complete nine-turn signal trigger scenario
        
        // 中文：首先填充足够的历史数据
        // English: First fill enough history data
        // 我们需要让每一天的价格都低于4天前的价格，持续9天
        
        // 初始5天数据: 20, 19, 18, 17, 16
        context.update(20.0);
        context.update(19.0);
        context.update(18.0);
        context.update(17.0);
        context.update(16.0);

        // 中文：连续9天，每天价格都低于4天前
        // English: 9 consecutive days, each day price lower than 4 days ago
        double[] newPrices = {15.0, 14.0, 13.0, 12.0, 11.0, 10.0, 9.0, 8.0, 7.0};
        
        boolean signalTriggered = false;
        int triggerDay = -1;
        
        for (int i = 0; i < newPrices.length; i++) {
            Tick tick = createTick(newPrices[i]);
            // 中文：先更新context（因为isMatch会用到最新价格）
            // English: Update context first (since isMatch uses latest price)
            context.update(newPrices[i]);
            
            boolean matched = strategy.isMatch(tick, context);
            if (matched) {
                signalTriggered = true;
                triggerDay = i + 1; // 1-indexed
                break;
            }
        }

        // 中文：应该在第9天触发信号
        // English: Should trigger signal on day 9
        assertTrue(signalTriggered, "九转信号应该被触发");
        assertEquals(9, triggerDay, "信号应该在第9天触发");
    }

    @Test
    @DisplayName("测试计数器重置（中途不满足条件）")
    void testCounterReset() {
        // 中文：填充历史数据
        // English: Fill history data
        for (int i = 0; i < 5; i++) {
            context.update(20.0 - i);
        }

        // 中文：连续3天满足条件
        // English: 3 consecutive days meet condition
        for (int i = 0; i < 3; i++) {
            Tick tick = createTick(14.0 - i);
            context.update(14.0 - i);
            strategy.isMatch(tick, context);
        }

        // 中文：第4天不满足条件（价格上涨）
        // English: Day 4 doesn't meet condition (price rises)
        Tick tickRise = createTick(20.0);
        context.update(20.0);
        assertFalse(strategy.isMatch(tickRise, context));

        // 中文：计数器应该被重置，需要重新累计9天
        // English: Counter should be reset, need to accumulate 9 days again
        Integer count = context.getStrategyPropOrDefault("NINE_TURN_COUNT", 0);
        assertEquals(0, count, "计数器应该被重置为0");
    }

    @Test
    @DisplayName("测试信号触发后计数器重置")
    void testCounterResetAfterSignal() {
        // 中文：构造能触发信号的场景
        // English: Construct scenario that triggers signal
        
        // 填充降序数据
        for (int i = 0; i < 15; i++) {
            context.update(30.0 - i);
        }

        // 中文：手动设置计数器为8（即将触发）
        // English: Manually set counter to 8 (about to trigger)
        context.setStrategyProp("NINE_TURN_COUNT", 8);

        // 中文：再次满足条件，应触发信号
        // English: Meet condition again, should trigger signal
        Tick tick = createTick(10.0);
        context.update(10.0);
        boolean matched = strategy.isMatch(tick, context);

        assertTrue(matched, "第9次满足条件应触发信号");

        // 中文：信号触发后计数器应重置
        // English: Counter should reset after signal
        Integer count = context.getStrategyPropOrDefault("NINE_TURN_COUNT", 0);
        assertEquals(0, count, "信号触发后计数器应重置为0");
    }

    /**
     * 创建测试用Tick对象
     */
    private Tick createTick(double price) {
        return Tick.builder()
                .symbol("000001.SZ")
                .price(price)
                .volume(1000L)
                .eventTime(System.currentTimeMillis())
                .build();
    }
}
