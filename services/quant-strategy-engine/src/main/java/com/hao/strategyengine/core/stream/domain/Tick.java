package com.hao.strategyengine.core.stream.domain;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 核心流式行情对象 (Tick)
 *
 * 设计原则：
 * 1. 对应 Kafka 传输的 HistoryTrendDTO，但做了内存优化。
 * 2. Immutable (不可变)，天然线程安全。
 * 3. 使用基本类型 (double/long) 替代包装类，减少 GC 压力。
 *
 * 字段映射关系：
 * | HistoryTrendDTO     | Tick          | 说明                |
 * |---------------------|---------------|---------------------|
 * | windCode            | symbol        | 股票代码             |
 * | latestPrice         | price         | 最新价               |
 * | averagePrice        | averagePrice  | 均价                 |
 * | totalVolume         | volume        | 总成交量 (转为long)  |
 * | tradeDate           | eventTime     | 时间戳 (转为毫秒)    |
 *
 * 为什么用基本类型：
 * - double 比 Double 少 16 字节对象头
 * - long 比 LocalDateTime 对象小得多，且 CPU 比较更快
 * - 高频场景下，GC 压力显著降低
 *
 * @author hli
 * @date 2026-01-02
 */
@Value
@Builder
public class Tick {

    /**
     * 股票代码 (对应 windCode)
     * e.g., "600519.SH", "000001.SZ"
     */
    String symbol;

    /**
     * 最新价 (对应 latestPrice)
     * 策略计算的核心字段
     */
    double price;

    /**
     * 均价 (对应 averagePrice)
     * VWAP、均值回归等策略可能需要
     */
    double averagePrice;

    /**
     * 总成交量 (对应 totalVolume)
     * 转换为 long，避免浮点数精度问题，且计算更快
     * 单位：手 (1手=100股)
     */
    long volume;

    /**
     * 事件时间戳 (毫秒) (对应 tradeDate)
     * 使用 long 进行时间窗口比较效率最高
     */
    long eventTime;

    /**
     * 辅助工厂方法：将 LocalDateTime 转为 long 时间戳
     *
     * 使用场景：
     * - StreamDispatchEngine 从 HistoryTrendDTO 转换时调用
     *
     * @param time LocalDateTime 对象
     * @return 毫秒级时间戳
     */
    public static long toTimestamp(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
