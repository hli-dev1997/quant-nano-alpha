package com.hao.strategyengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流式计算配置属性类
 *
 * 设计目的：
 * 1. 集中管理流式计算引擎的所有可配置参数。
 * 2. 通过@ConfigurationProperties实现类型安全的配置绑定。
 * 3. 提供合理的默认值，开箱即用。
 *
 * 配置示例（application.yml）：
 * <pre>
 * stream:
 *   compute:
 *     worker-threads: 8
 *     history-size: 250
 *     redis-key-prefix: STRATEGY
 *     redis-ttl-hours: 48
 * </pre>
 *
 * 使用场景：
 * - StreamComputeEngine初始化线程池
 * - StockDomainContext初始化RingBuffer容量
 * - RedisStrategyRepository配置Key前缀和TTL
 *
 * @author hli
 * @date 2026-01-02
 */
@Data
@Component
@ConfigurationProperties(prefix = "stream.compute")
public class StreamComputeProperties {

    /**
     * Worker线程数
     * 默认值：CPU核心数
     * 建议：设置为CPU核心数，避免过多线程切换开销
     */
    private int workerThreads = Runtime.getRuntime().availableProcessors();

    /**
     * 历史数据容量（RingBuffer大小）
     * 默认值：250（约1个交易年度）
     * 说明：决定StockDomainContext中prices数组的大小
     */
    private int historySize = 250;

    /**
     * Redis Key前缀
     * 默认值：STRATEGY
     * 完整Key格式：{prefix}:{strategyId}:{date}
     */
    private String redisKeyPrefix = "STRATEGY";

    /**
     * Redis TTL（小时）
     * 默认值：48小时
     * 说明：策略命中结果的过期时间
     */
    private int redisTtlHours = 48;

    /**
     * 是否启用流式计算
     * 默认值：true
     * 说明：可用于灰度发布时控制是否启用新架构
     */
    private boolean enabled = true;

    /**
     * Context预热批量大小
     * 默认值：100
     * 说明：启动时批量加载历史数据的股票数量
     */
    private int warmupBatchSize = 100;
}
