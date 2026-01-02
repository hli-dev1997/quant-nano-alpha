package com.hao.strategyengine.core.stream.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockDomainContext单元测试
 *
 * 测试目的：
 * 1. 验证RingBuffer环形数组的正确性（写入、回绕、读取）。
 * 2. 验证边界条件处理（空数据、越界访问）。
 * 3. 验证策略属性存储功能。
 *
 * 设计思路：
 * - 覆盖正常流程和边界情况
 * - 验证O(1)历史访问的正确性
 * - 确保环形回绕逻辑正确
 *
 * @author hli
 * @date 2026-01-02
 */
class StockDomainContextTest {

    @Test
    @DisplayName("测试基本更新和读取功能")
    void testUpdateAndGet() {
        // 中文：创建容量为5的Context
        // English: Create context with capacity 5
        StockDomainContext context = new StockDomainContext("000001.SZ", 5);

        // 中文：写入3个价格
        // English: Write 3 prices
        context.update(10.0);
        context.update(11.0);
        context.update(12.0);

        // 中文：验证数据量
        // English: Verify data size
        assertEquals(3, context.getSize());

        // 中文：验证价格读取（0=最新，1=昨日，2=前日）
        // English: Verify price reading (0=latest, 1=yesterday, 2=day before)
        assertEquals(12.0, context.getPrice(0), 0.001);
        assertEquals(11.0, context.getPrice(1), 0.001);
        assertEquals(10.0, context.getPrice(2), 0.001);
    }

    @Test
    @DisplayName("测试环形回绕逻辑")
    void testRingBufferWrap() {
        // 中文：创建容量为3的Context
        // English: Create context with capacity 3
        StockDomainContext context = new StockDomainContext("000002.SZ", 3);

        // 中文：写入5个价格，触发回绕覆盖
        // English: Write 5 prices, trigger wrap and overwrite
        context.update(10.0); // index 0
        context.update(11.0); // index 1
        context.update(12.0); // index 2
        context.update(13.0); // index 0 (覆盖10.0)
        context.update(14.0); // index 1 (覆盖11.0)

        // 中文：size应该保持为capacity
        // English: Size should stay at capacity
        assertEquals(3, context.getSize());

        // 中文：验证最新3个价格
        // English: Verify latest 3 prices
        assertEquals(14.0, context.getPrice(0), 0.001); // 最新
        assertEquals(13.0, context.getPrice(1), 0.001); // 次新
        assertEquals(12.0, context.getPrice(2), 0.001); // 第三新

        // 中文：10.0和11.0已被覆盖，无法访问
        // English: 10.0 and 11.0 are overwritten, cannot access
    }

    @Test
    @DisplayName("测试边界条件-空数据")
    void testEmptyContext() {
        StockDomainContext context = new StockDomainContext("000003.SZ", 5);

        // 中文：空Context，size应为0
        // English: Empty context, size should be 0
        assertEquals(0, context.getSize());

        // 中文：读取任何历史价格都应返回NaN
        // English: Reading any history should return NaN
        assertTrue(Double.isNaN(context.getPrice(0)));
        assertTrue(Double.isNaN(context.getPrice(1)));
    }

    @Test
    @DisplayName("测试边界条件-越界访问")
    void testOutOfBoundsAccess() {
        StockDomainContext context = new StockDomainContext("000004.SZ", 5);

        // 中文：写入3个价格
        // English: Write 3 prices
        context.update(10.0);
        context.update(11.0);
        context.update(12.0);

        // 中文：访问超出范围应返回NaN
        // English: Accessing out of range should return NaN
        assertTrue(Double.isNaN(context.getPrice(3))); // 只有3个数据，索引3越界
        assertTrue(Double.isNaN(context.getPrice(100)));
        assertTrue(Double.isNaN(context.getPrice(-1)));
    }

    @Test
    @DisplayName("测试策略属性存储")
    void testStrategyProps() {
        StockDomainContext context = new StockDomainContext("000005.SZ", 5);

        // 中文：设置策略属性
        // English: Set strategy property
        context.setStrategyProp("NINE_TURN_COUNT", 5);
        context.setStrategyProp("MA_SIGNAL", "GOLDEN_CROSS");

        // 中文：读取策略属性
        // English: Read strategy property
        Integer count = context.getStrategyProp("NINE_TURN_COUNT");
        assertEquals(5, count);

        String signal = context.getStrategyProp("MA_SIGNAL");
        assertEquals("GOLDEN_CROSS", signal);

        // 中文：测试默认值
        // English: Test default value
        Integer missing = context.getStrategyPropOrDefault("MISSING_KEY", 0);
        assertEquals(0, missing);
    }

    @Test
    @DisplayName("测试历史数据充足性判断")
    void testHasEnoughHistory() {
        StockDomainContext context = new StockDomainContext("000006.SZ", 10);

        // 中文：初始状态，数据不足
        // English: Initial state, insufficient data
        assertFalse(context.hasEnoughHistory(5));

        // 中文：写入5个价格
        // English: Write 5 prices
        for (int i = 0; i < 5; i++) {
            context.update(10.0 + i);
        }

        // 中文：现在有5个数据
        // English: Now have 5 data points
        assertTrue(context.hasEnoughHistory(5));
        assertFalse(context.hasEnoughHistory(6));
    }

    @Test
    @DisplayName("测试大数据量下的正确性")
    void testLargeDataVolume() {
        // 中文：使用默认容量250
        // English: Use default capacity 250
        StockDomainContext context = new StockDomainContext("000007.SZ");

        // 中文：写入1000个价格，多次回绕
        // English: Write 1000 prices, multiple wraps
        for (int i = 0; i < 1000; i++) {
            context.update(100.0 + i * 0.1);
        }

        // 中文：size应该是250（容量上限）
        // English: Size should be 250 (capacity limit)
        assertEquals(250, context.getSize());

        // 中文：验证最新价格
        // English: Verify latest price
        double expectedLatest = 100.0 + 999 * 0.1; // 199.9
        assertEquals(expectedLatest, context.getPrice(0), 0.001);

        // 中文：验证249天前的价格
        // English: Verify price from 249 days ago
        double expected249DaysAgo = 100.0 + 750 * 0.1; // 175.0
        assertEquals(expected249DaysAgo, context.getPrice(249), 0.001);
    }
}
