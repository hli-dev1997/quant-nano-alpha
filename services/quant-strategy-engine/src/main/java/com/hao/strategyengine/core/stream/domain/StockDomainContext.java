package com.hao.strategyengine.core.stream.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * 股票领域上下文（Memento模式应用）
 *
 * 设计目的：
 * 1. 维护单只股票的内存状态，包括历史价格、策略计数器等。
 * 2. 使用环形数组（RingBuffer）实现O(1)时间复杂度的历史数据访问。
 * 3. 线程封闭设计，由固定Worker线程独占访问，无需同步机制。
 *
 * 实现思路：
 * - prices[] 为环形数组，固定容量，cursor指向最新写入位置
 * - 写入时cursor循环递增，自动覆盖最旧数据
 * - getPrice(n) 通过cursor回溯计算索引，实现O(1)历史访问
 *
 * 核心算法（环形数组）：
 * 假设容量为5，依次写入 [10,20,30,40,50,60]
 * 数组状态: [60,20,30,40,50]，cursor=0
 * getPrice(0) → 60（最新），getPrice(1) → 50（昨日）
 *
 * 线程安全说明：
 * - 本类实例通过Thread Confinement模式保证安全
 * - 同一symbol的所有操作由Hash(symbol) % N固定分配给某个Worker
 * - 因此无需synchronized/lock，提升性能
 *
 * @author hli
 * @date 2026-01-02
 */
public class StockDomainContext {

    /**
     * 默认历史数据容量
     * 250个交易日约等于1个自然年，满足大多数技术指标需求
     */
    private static final int DEFAULT_CAPACITY = 250;

    /**
     * 股票代码
     */
    private final String symbol;

    /**
     * 环形数组-存储历史价格
     * 设计原因：使用原始数组而非LinkedList，减少GC压力和内存碎片
     */
    private final double[] prices;

    /**
     * 环形数组容量
     */
    private final int capacity;

    /**
     * 游标-指向最新数据写入位置
     * 初始值-1表示尚未写入任何数据
     */
    private int cursor = -1;

    /**
     * 实际数据量
     * 在数组填满前，size < capacity；填满后，size = capacity
     */
    private int size = 0;

    /**
     * 策略属性存储
     * 用于各策略存储自己的状态（如九转计数器）
     * Key: 策略ID + 属性名，Value: 属性值
     */
    private final Map<String, Object> strategyProps;

    /**
     * 默认容量构造器
     *
     * @param symbol 股票代码
     */
    public StockDomainContext(String symbol) {
        this(symbol, DEFAULT_CAPACITY);
    }

    /**
     * 自定义容量构造器
     *
     * @param symbol   股票代码
     * @param capacity 历史数据容量
     */
    public StockDomainContext(String symbol, int capacity) {
        this.symbol = symbol;
        this.capacity = capacity;
        this.prices = new double[capacity];
        this.strategyProps = new HashMap<>();
    }

    /**
     * 更新价格数据
     *
     * 实现逻辑：
     * 1. cursor循环递增，到达capacity后回绕到0
     * 2. 在cursor位置写入新价格
     * 3. size增长直到达到capacity
     *
     * 时间复杂度：O(1)
     *
     * @param price 最新价格
     */
    public void update(double price) {
        // 中文：游标循环递增，实现环形写入
        // English: Cursor increments circularly for ring buffer write
        cursor = (cursor + 1) % capacity;
        prices[cursor] = price;

        // 中文：size在未填满时递增，填满后保持capacity
        // English: Size increments until capacity is reached
        if (size < capacity) {
            size++;
        }
    }

    /**
     * 获取N天前的价格
     *
     * 实现逻辑：
     * 1. daysAgo=0 表示最新价格，daysAgo=1 表示昨日价格
     * 2. 通过cursor回溯计算实际数组索引
     * 3. 环形回绕公式：index = (cursor - daysAgo + capacity) % capacity
     *
     * 边界处理：
     * - 如果daysAgo超出已有数据范围，返回NaN
     *
     * 时间复杂度：O(1)
     *
     * @param daysAgo 回溯天数（0表示今天/最新）
     * @return 历史价格，数据不足时返回Double.NaN
     */
    public double getPrice(int daysAgo) {
        // 中文：边界检查-请求的天数超出已有数据
        // English: Boundary check - requested days exceeds available data
        if (daysAgo < 0 || daysAgo >= size) {
            return Double.NaN;
        }

        // 中文：环形数组索引计算
        // English: Ring buffer index calculation
        // 公式说明：cursor指向最新数据，减去daysAgo得到历史位置
        // 加capacity再取模，处理负数情况（环形回绕）
        int index = (cursor - daysAgo + capacity) % capacity;
        return prices[index];
    }

    /**
     * 获取已有数据量
     *
     * @return 当前存储的价格数据数量
     */
    public int getSize() {
        return size;
    }

    /**
     * 获取股票代码
     *
     * @return 股票代码
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 获取策略属性值
     *
     * @param key 属性键（建议格式：策略ID_属性名）
     * @param <T> 属性值类型
     * @return 属性值，不存在返回null
     */
    @SuppressWarnings("unchecked")
    public <T> T getStrategyProp(String key) {
        return (T) strategyProps.get(key);
    }

    /**
     * 设置策略属性值
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setStrategyProp(String key, Object value) {
        strategyProps.put(key, value);
    }

    /**
     * 获取策略属性，如果不存在则返回默认值
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @param <T>          属性值类型
     * @return 属性值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T getStrategyPropOrDefault(String key, T defaultValue) {
        Object value = strategyProps.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 判断历史数据是否足够
     *
     * @param requiredDays 所需天数
     * @return true-数据充足，false-数据不足
     */
    public boolean hasEnoughHistory(int requiredDays) {
        return size >= requiredDays;
    }
}
