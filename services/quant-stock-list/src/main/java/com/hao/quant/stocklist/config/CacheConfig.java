package com.hao.quant.stocklist.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类 (Cache Configuration)
 * <p>
 * 类职责：
 * 配置本地 Caffeine 缓存（L1），实现极速读取。
 * <p>
 * 设计目的：
 * 1. Caffeine 作为一级缓存（L1），TTL 3 秒，提供极速本地读取
 * 2. 采用 expireAfterWrite，写入后固定时间过期
 * 3. 最大容量 1000 条，使用 W-TinyLFU 淘汰策略
 *
 * @author hli
 * @date 2026-01-30
 */
@Configuration
public class CacheConfig {

    /**
     * 股票信号本地缓存
     * <p>
     * Key: 缓存键（如 stock:signal:list:RED_NINE_TURN:2026-01-30）
     * Value: 信号列表 JSON 字符串
     * <p>
     * 配置：
     * - expireAfterWrite: 3 秒（写入后 3 秒过期）
     * - maximumSize: 1000 条
     *
     * @return Caffeine Cache 实例
     */
    @Bean("stockSignalCache")
    public Cache<String, List<String>> stockSignalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)  // L1 缓存 3 秒过期
                .maximumSize(1000)                       // 最大 1000 条
                .recordStats()                           // 开启统计（便于监控）
                .build();
    }
}
