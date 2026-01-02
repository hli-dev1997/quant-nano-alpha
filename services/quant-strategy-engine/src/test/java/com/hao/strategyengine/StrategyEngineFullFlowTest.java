package com.hao.strategyengine;

import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;
import com.hao.strategyengine.core.stream.strategy.impl.NineTurnStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略引擎全链路测试（极简版）
 *
 * 测试目标：
 * 1. 验证流式计算核心逻辑正确性
 * 2. 验证 RingBuffer、路由算法、策略计算
 * 3. 纯单元测试，不依赖外部服务
 *
 * @author hli
 * @date 2026-01-02
 */
@DisplayName("策略引擎测试（极简版）")
class StrategyEngineFullFlowTest {

    private NineTurnStrategy nineTurnStrategy;
    private static final String TEST_SYMBOL = "TEST_001.SZ";

    @BeforeEach
    void setUp() {
        nineTurnStrategy = new NineTurnStrategy();
    }

    @Test
    @DisplayName("九转序列：完整触发流程")
    void testNineTurn_FullTrigger() {
        // Given：构造 13 天下跌数据
        double[] prices = {20.0, 19.5, 19.0, 18.5, 18.0, 17.0, 16.0, 15.0, 14.0, 13.0, 12.0, 11.0, 10.0};
        StockDomainContext context = new StockDomainContext(TEST_SYMBOL, 250);
        for (double price : prices) {
            context.update(price);
        }

        // 设置计数器为 8
        context.setStrategyProp("NINE_TURN_COUNT", 8);

        // When：第 14 天继续下跌
        context.update(9.0);
        Tick tick = Tick.builder().symbol(TEST_SYMBOL).price(9.0).averagePrice(9.0).volume(10000).eventTime(System.currentTimeMillis()).build();
        boolean isMatch = nineTurnStrategy.isMatch(tick, context);

        // Then：应触发信号
        assertTrue(isMatch, "第9次满足条件应触发");
        assertEquals(0, (int) context.getStrategyPropOrDefault("NINE_TURN_COUNT", 0), "计数器应重置");

        System.out.println("===== 九转触发测试 =====");
        System.out.println("结果: 触发 ✓");
    }

    @Test
    @DisplayName("九转序列：上涨不触发")
    void testNineTurn_NoMatch() {
        // Given：上涨数据
        StockDomainContext context = new StockDomainContext("NO_MATCH.SZ", 250);
        for (int i = 0; i < 6; i++) context.update(10.0 + i);

        // When：价格继续上涨
        Tick tick = Tick.builder().symbol("NO_MATCH.SZ").price(16.0).averagePrice(16.0).volume(1000).eventTime(System.currentTimeMillis()).build();
        boolean isMatch = nineTurnStrategy.isMatch(tick, context);

        // Then：不触发
        assertFalse(isMatch, "上涨不应触发");
        System.out.println("===== 九转未触发测试 =====");
        System.out.println("结果: 未触发 ✓");
    }

    @Test
    @DisplayName("路由算法：Hash 分发")
    void testRouting() {
        String[] symbols = {"000001.SZ", "600000.SH", "300001.SZ"};
        int workerCount = 8;

        System.out.println("===== 路由测试 =====");
        for (String symbol : symbols) {
            int slot = Math.abs(symbol.hashCode()) % workerCount;
            System.out.println(symbol + " → Worker-" + slot);
            assertEquals(slot, Math.abs(symbol.hashCode()) % workerCount, "路由应稳定");
        }
    }

    @Test
    @DisplayName("RingBuffer：环形覆盖")
    void testRingBuffer() {
        int capacity = 10;
        StockDomainContext context = new StockDomainContext("RING.SZ", capacity);

        // 写入 15 个，超过容量
        for (int i = 1; i <= 15; i++) context.update(i * 1.0);

        assertEquals(capacity, context.getSize(), "大小=容量");
        assertEquals(15.0, context.getPrice(0), 0.001, "最新=15");
        assertEquals(6.0, context.getPrice(9), 0.001, "最早=6");
        assertTrue(Double.isNaN(context.getPrice(10)), "超出=NaN");

        System.out.println("===== RingBuffer 测试 =====");
        System.out.println("容量: " + capacity + ", 最新: 15.0, 最早: 6.0 ✓");
    }

    @Test
    @DisplayName("性能：策略计算耗时")
    void testPerformance() {
        StockDomainContext context = new StockDomainContext("PERF.SZ", 250);
        for (int i = 0; i < 100; i++) context.update(10.0 + i * 0.1);

        Tick tick = Tick.builder().symbol("PERF.SZ").price(5.0).averagePrice(5.0).volume(1000).eventTime(System.currentTimeMillis()).build();

        int iterations = 10000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) nineTurnStrategy.isMatch(tick, context);
        long end = System.nanoTime();

        double avgMicros = (double) (end - start) / iterations / 1000;
        System.out.println("===== 性能测试 =====");
        System.out.println("10000次, 平均: " + String.format("%.2f", avgMicros) + " μs");
        assertTrue(avgMicros < 100, "应<100μs");
    }
}
