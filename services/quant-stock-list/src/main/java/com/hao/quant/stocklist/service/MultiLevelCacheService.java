package com.hao.quant.stocklist.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存服务 (Multi-Level Cache Service)
 * <p>
 * 类职责：
 * 实现三级缓存的查询策略：
 * L1 Caffeine（3秒） -> L2 Redis -> L3 MySQL（兜底）
 * <p>
 * 使用场景：
 * StablePicksController 查询每日精选股票列表时调用。
 * <p>
 * 核心设计：
 * 1. Caffeine 作为一级本地缓存，TTL 3 秒，极速读取
 * 2. Redis 作为二级分布式缓存，由信号中心主动推送
 * 3. MySQL 作为三级兜底，通过 Redisson 分布式锁防止缓存击穿
 * 4. 参考 RedisStudy 项目的 CacheBreakdownUtil 实现
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    /**
     * Redis Key 前缀：股票信号列表
     * 格式：stock:signal:list:{策略名}:{交易日}
     */
    private static final String REDIS_KEY_PREFIX = "stock:signal:list:";

    /**
     * 分布式锁 Key 前缀
     */
    private static final String LOCK_KEY_PREFIX = "lock:stock:signal:";

    /**
     * 获取锁等待时间（秒）
     */
    private static final long LOCK_WAIT_TIME = 1;

    /**
     * 锁自动释放时间（秒）
     * Redisson 会自动续期（看门狗机制）
     */
    private static final long LOCK_LEASE_TIME = 10;

    @Autowired
    @Qualifier("stockSignalCache")
    private Cache<String, List<String>> caffeineCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 多级缓存查询股票信号列表
     * <p>
     * 查询顺序：
     * 1. L1 Caffeine 本地缓存（3 秒 TTL）
     * 2. L2 Redis 分布式缓存
     * 3. L3 MySQL 数据库（通过分布式锁保护）
     *
     * @param strategyName 策略名称
     * @param tradeDate    交易日字符串（yyyy-MM-dd）
     * @return 信号列表（JSON 字符串列表）
     */
    public List<String> querySignals(String strategyName, String tradeDate) {
        String cacheKey = buildCacheKey(strategyName, tradeDate);

        // 1. L1 Caffeine 查询
        List<String> signals = caffeineCache.getIfPresent(cacheKey);
        if (signals != null) {
            log.debug("L1缓存命中|L1_cache_hit,key={}", cacheKey);
            return signals;
        }

        // 2. L2 Redis 查询
        signals = queryFromRedis(cacheKey);
        if (signals != null && !signals.isEmpty()) {
            log.debug("L2缓存命中|L2_cache_hit,key={},size={}", cacheKey, signals.size());
            // 回填 L1
            caffeineCache.put(cacheKey, signals);
            return signals;
        }

        // 3. L3 MySQL 查询（带分布式锁保护）
        return queryFromDbWithLock(strategyName, tradeDate, cacheKey);
    }

    /**
     * 从 Redis 查询信号列表
     *
     * @param cacheKey 缓存键
     * @return 信号列表，不存在返回 null
     */
    private List<String> queryFromRedis(String cacheKey) {
        try {
            List<String> signals = redisTemplate.opsForList().range(cacheKey, 0, -1);
            return (signals != null && !signals.isEmpty()) ? signals : null;
        } catch (Exception e) {
            log.warn("Redis查询失败|Redis_query_failed,key={},error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /**
     * 从 MySQL 查询信号列表（带分布式锁保护）
     * <p>
     * 使用 Redisson 分布式锁，防止缓存击穿：
     * 1. 尝试获取锁（等待 1 秒）
     * 2. 成功获取锁后，再次检查缓存（双重检查）
     * 3. 缓存未命中则查询 MySQL 并回填缓存
     * 4. 未获取到锁则等待后重试 Redis
     *
     * @param strategyName 策略名称
     * @param tradeDate    交易日
     * @param cacheKey     缓存键
     * @return 信号列表
     */
    private List<String> queryFromDbWithLock(String strategyName, String tradeDate, String cacheKey) {
        String lockKey = LOCK_KEY_PREFIX + strategyName + ":" + tradeDate;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 尝试获取锁，等待 1 秒，自动续期 10 秒
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (locked) {
                // 双重检查：再次查询 Redis
                List<String> signals = queryFromRedis(cacheKey);
                if (signals != null && !signals.isEmpty()) {
                    log.debug("获取锁后L2命中|L2_hit_after_lock,key={}", cacheKey);
                    caffeineCache.put(cacheKey, signals);
                    return signals;
                }

                // 查询 MySQL（此处为占位，实际需要注入 Mapper）
                log.info("L3数据库查询|L3_db_query,strategy={},date={}", strategyName, tradeDate);
                // TODO: 注入 StockSignalMapper 并查询
                // List<StockSignal> dbSignals = stockSignalMapper.selectPassedSignals(strategyName, LocalDate.parse(tradeDate));
                // signals = dbSignals.stream().map(JsonUtil::toJson).collect(Collectors.toList());

                // 当前返回空列表，实际实现时替换为数据库查询结果
                signals = Collections.emptyList();

                // 回填 Redis（只有非空才回填）
                if (signals != null && !signals.isEmpty()) {
                    try {
                        redisTemplate.opsForList().rightPushAll(cacheKey, signals.toArray(new String[0]));
                        redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
                        log.info("L3查询结果回填Redis|L3_result_cached,key={},size={}", cacheKey, signals.size());
                    } catch (Exception e) {
                        log.warn("Redis回填失败|Redis_cache_refill_failed,key={}", cacheKey, e);
                    }
                }

                // 回填 Caffeine
                caffeineCache.put(cacheKey, signals != null ? signals : Collections.emptyList());
                return signals;
            } else {
                // 未获取到锁，等待后重试 Redis
                log.debug("获取锁失败_重试Redis|Lock_failed_retry_redis,key={}", cacheKey);
                Thread.sleep(100);
                List<String> signals = queryFromRedis(cacheKey);
                return signals != null ? signals : Collections.emptyList();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断|Lock_interrupted,key={}", cacheKey);
            return Collections.emptyList();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 构建缓存键
     *
     * @param strategyName 策略名称
     * @param tradeDate    交易日
     * @return 缓存键
     */
    private String buildCacheKey(String strategyName, String tradeDate) {
        return REDIS_KEY_PREFIX + strategyName + ":" + tradeDate;
    }

    /**
     * 手动刷新本地缓存（供测试使用）
     *
     * @param strategyName 策略名称
     * @param tradeDate    交易日
     */
    public void invalidateLocalCache(String strategyName, String tradeDate) {
        String cacheKey = buildCacheKey(strategyName, tradeDate);
        caffeineCache.invalidate(cacheKey);
        log.info("本地缓存已失效|Local_cache_invalidated,key={}", cacheKey);
    }
}
