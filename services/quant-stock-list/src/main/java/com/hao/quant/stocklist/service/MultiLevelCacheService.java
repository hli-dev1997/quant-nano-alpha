package com.hao.quant.stocklist.service;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.hao.quant.stocklist.mapper.StockSignalMapper;
import com.hao.quant.stocklist.model.StockSignal;
import constants.RedisKeyConstants;
import constants.SentinelResourceConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import util.JsonUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 多级缓存服务 (Multi-Level Cache Service)
 * <p>
 * 三层保护的高并发设计：
 * L1 Caffeine（3s TTL）-> L2 Redis（24h）-> L3 MySQL（兜底）
 * <p>
 * 保护机制：
 * 1. L1->L2: Redis 分布式锁，本地缓存过期后只有1个线程查 Redis
 * 2. L2->L3: Sentinel 限流（QPS=1），Redis 无数据时只有1个线程查 MySQL
 * 3. 空值保护: EMPTY_MARKER 特殊标记，查无数据时缓存标记，避免重复穿透
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    /**
     * Redis Key 前缀：使用统一常量
     */
    private static final String REDIS_KEY_PREFIX = RedisKeyConstants.STOCK_SIGNAL_LIST_PREFIX;

    /**
     * 分布式锁 Key 前缀：使用统一常量
     */
    private static final String LOCK_KEY_PREFIX = RedisKeyConstants.STOCK_SIGNAL_LOCK_PREFIX;

    /**
     * 空值标记：使用统一常量
     */
    private static final String EMPTY_MARKER = RedisKeyConstants.CACHE_EMPTY_MARKER;

    /**
     * Sentinel 资源名称：L3 数据库查询
     */
    private static final String SENTINEL_RESOURCE_L3_QUERY = SentinelResourceConstants.STOCK_LIST_L3_QUERY;

    /**
     * 获取锁等待时间（秒）
     */
    private static final long LOCK_WAIT_TIME = 1;

    /**
     * 锁自动释放时间（秒），Redisson 会自动续期（看门狗机制）
     */
    private static final long LOCK_LEASE_TIME = 10;

    /**
     * 正常数据缓存过期时间：24 小时
     */
    private static final long CACHE_TTL_HOURS = 24;

    /**
     * 空值标记缓存过期时间：1 分钟
     */
    private static final long EMPTY_CACHE_TTL_MINUTES = 1;

    @Autowired
    @Qualifier("stockSignalCache")
    private Cache<String, List<String>> caffeineCache;



    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StockSignalMapper stockSignalMapper;

    /**
     * 初始化 Sentinel 限流规则
     */
    @PostConstruct
    public void initSentinelRules() {
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource(SENTINEL_RESOURCE_L3_QUERY);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);  // QPS = 1，只允许1个请求/秒进入L3
        rule.setLimitApp("default");
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
        log.info("Sentinel规则初始化完成|Sentinel_rules_loaded,resource={},qps=1", SENTINEL_RESOURCE_L3_QUERY);
    }

    /**
     * 多级缓存查询股票信号列表
     * <p>
     * 查询顺序：
     * 1. L1 Caffeine 本地缓存（3 秒 TTL）
     * 2. L2 Redis 分布式缓存（带分布式锁保护）
     * 3. L3 MySQL 数据库（带 Sentinel 限流保护）
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日字符串（yyyy-MM-dd）
     * @return 信号列表（JSON 字符串列表）
     */
    public List<String> querySignals(String strategyId, String tradeDate) {
        String cacheKey = buildCacheKey(strategyId, tradeDate);

        // 1. L1 Caffeine 查询
        List<String> signals = caffeineCache.getIfPresent(cacheKey);
        if (signals != null) {
            log.debug("L1缓存命中|L1_cache_hit,key={}", cacheKey);
            return signals;
        }

        // 2. L2 Redis 查询（带分布式锁保护）
        return queryFromRedisWithLock(strategyId, tradeDate, cacheKey);
    }

    /**
     * 从 Redis 查询（带分布式锁保护）
     * <p>
     * 本地缓存过期后，只有 1 个线程能进入查 Redis
     */
    private List<String> queryFromRedisWithLock(String strategyId, String tradeDate, String cacheKey) {
        String lockKey = LOCK_KEY_PREFIX + "redis:" + strategyId + ":" + tradeDate;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (locked) {
                // 双重检查：再次查询 L1
                List<String> signals = caffeineCache.getIfPresent(cacheKey);
                if (signals != null) {
                    log.debug("获取锁后L1命中|L1_hit_after_lock,key={}", cacheKey);
                    return signals;
                }

                // 查询 Redis
                signals = queryFromRedis(cacheKey);
                if (signals != null) {
                    // 回填 L1
                    caffeineCache.put(cacheKey, signals);
                    return signals;
                }

                // L2 未命中，进入 L3（带 Sentinel 限流）
                return queryFromDbWithSentinel(strategyId, tradeDate, cacheKey);
            } else {
                // 未获取到锁，等待后重试
                log.debug("获取锁失败_重试|Lock_failed_retry,key={}", cacheKey);
                Thread.sleep(100);
                List<String> signals = caffeineCache.getIfPresent(cacheKey);
                if (signals != null) {
                    return signals;
                }
                signals = queryFromRedis(cacheKey);
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
     * 从 Redis 查询信号列表
     * 使用 Redisson 原生 API 避免 Spring Data Redis 兼容性问题
     *
     * @param cacheKey 缓存键
     * @return 信号列表，不存在返回 null，空标记返回空列表
     */
    private List<String> queryFromRedis(String cacheKey) {
        try {
            RList<String> rlist = redissonClient.getList(cacheKey);
            List<String> signals = rlist.readAll();

            if (signals == null || signals.isEmpty()) {
                return null;
            }

            // 检查是否是空值标记
            if (signals.size() == 1 && EMPTY_MARKER.equals(signals.get(0))) {
                log.debug("L2空值标记命中|L2_empty_marker_hit,key={}", cacheKey);
                return Collections.emptyList();
            }

            log.debug("L2缓存命中|L2_cache_hit,key={},size={}", cacheKey, signals.size());
            return signals;
        } catch (Exception e) {
            log.warn("Redis查询失败|Redis_query_failed,key={},error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /**
     * 从 MySQL 查询信号列表（带 Sentinel 限流保护）
     * <p>
     * Sentinel 限流 QPS=1，防止大量请求同时穿透到数据库
     */
    private List<String> queryFromDbWithSentinel(String strategyId, String tradeDate, String cacheKey) {
        Entry entry = null;
        try {
            // Sentinel 限流入口
            entry = SphU.entry(SENTINEL_RESOURCE_L3_QUERY);

            // 查询 MySQL
            log.info("L3数据库查询|L3_db_query,strategy={},date={}", strategyId, tradeDate);
            List<StockSignal> dbSignals = stockSignalMapper.selectPassedSignals(strategyId, tradeDate);

            List<String> signals;
            if (dbSignals == null || dbSignals.isEmpty()) {
                // 查无数据，缓存空值标记
                signals = Collections.emptyList();
                cacheEmptyMarker(cacheKey);
            } else {
                // 转换为 JSON 列表
                signals = dbSignals.stream()
                        .map(JsonUtil::toJson)
                        .collect(Collectors.toList());
                // 回填 Redis
                cacheToRedis(cacheKey, signals);
            }

            // 回填 L1
            caffeineCache.put(cacheKey, signals);
            return signals;

        } catch (BlockException e) {
            // 被限流，返回空列表（不穿透到数据库）
            log.warn("L3被限流|L3_blocked_by_sentinel,strategy={},date={}", strategyId, tradeDate);
            return Collections.emptyList();
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 缓存数据到 Redis
     * 使用 Redisson 原生 API 避免 Spring Data Redis 兼容性问题
     */
    private void cacheToRedis(String cacheKey, List<String> signals) {
        try {
            RList<String> rlist = redissonClient.getList(cacheKey);
            rlist.addAll(signals);
            rlist.expire(Duration.ofHours(CACHE_TTL_HOURS));
            log.info("L3结果回填Redis|L3_result_cached,key={},size={}", cacheKey, signals.size());
        } catch (Exception e) {
            log.warn("Redis回填失败|Redis_cache_refill_failed,key={}", cacheKey, e);
        }
    }

    /**
     * 缓存空值标记到 Redis（防止缓存击穿）
     * 使用 Redisson 原生 API 避免 Spring Data Redis 兼容性问题
     */
    private void cacheEmptyMarker(String cacheKey) {
        try {
            RList<String> rlist = redissonClient.getList(cacheKey);
            rlist.add(EMPTY_MARKER);
            rlist.expire(Duration.ofMinutes(EMPTY_CACHE_TTL_MINUTES));
            log.info("空值标记已缓存|Empty_marker_cached,key={},ttl={}min", cacheKey, EMPTY_CACHE_TTL_MINUTES);
        } catch (Exception e) {
            log.warn("空值标记缓存失败|Empty_marker_cache_failed,key={}", cacheKey, e);
        }
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String strategyId, String tradeDate) {
        return REDIS_KEY_PREFIX + strategyId + ":" + tradeDate;
    }

    /**
     * 手动刷新本地缓存（供测试使用）
     */
    public void invalidateLocalCache(String strategyId, String tradeDate) {
        String cacheKey = buildCacheKey(strategyId, tradeDate);
        caffeineCache.invalidate(cacheKey);
        log.info("本地缓存已失效|Local_cache_invalidated,key={}", cacheKey);
    }

    /**
     * 清理 Redis 缓存（供测试使用，防止脏数据）
     * 使用 Redisson 原生 API 避免与 Spring Data Redis 的兼容性问题
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日
     */
    public void invalidateRedisCache(String strategyId, String tradeDate) {
        String cacheKey = buildCacheKey(strategyId, tradeDate);
        try {
            // 使用 Redisson 原生 API 删除 key，避免 Spring Data Redis 的 pExpire 递归问题
            boolean deleted = redissonClient.getBucket(cacheKey).delete();
            log.info("Redis缓存已清理|Redis_cache_invalidated,key={},deleted={}", cacheKey, deleted);
        } catch (Exception e) {
            log.warn("Redis缓存清理失败|Redis_cache_invalidate_failed,key={}", cacheKey, e);
        }
    }

    /**
     * 清理所有缓存（本地 + Redis）
     */
    public void invalidateAllCache(String strategyId, String tradeDate) {
        invalidateLocalCache(strategyId, tradeDate);
        invalidateRedisCache(strategyId, tradeDate);
    }
}
