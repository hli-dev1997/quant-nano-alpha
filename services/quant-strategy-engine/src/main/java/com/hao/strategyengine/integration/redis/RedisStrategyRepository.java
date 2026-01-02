package com.hao.strategyengine.integration.redis;

import com.hao.strategyengine.config.StreamComputeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 策略结果Redis存储仓库
 *
 * 设计目的：
 * 1. 封装策略命中结果的Redis存储逻辑。
 * 2. 实现按日期隔离的Key命名规范。
 * 3. 提供统一的TTL管理和批量操作能力。
 *
 * Key设计规范：
 * - 格式：{prefix}:{strategyId}:{yyyyMMdd}
 * - 示例：STRATEGY:NINE_TURN:20260102
 * - 数据结构：Redis Set（便于去重和交集/并集运算）
 *
 * 为什么使用Set而非List：
 * 1. 自动去重：同一股票多次命中只保留一条
 * 2. O(1)成员检查：快速判断某股票是否命中
 * 3. 集合运算：支持多策略结果的交集/并集
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStrategyRepository {

    /**
     * Redis操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 配置属性
     */
    private final StreamComputeProperties properties;

    /**
     * 日期格式化器
     * 使用yyyyMMdd格式，简洁且便于排序
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 保存策略命中记录
     *
     * 实现逻辑：
     * 1. 构建Redis Key（含策略ID和日期）
     * 2. 使用SADD将股票代码加入Set
     * 3. 设置TTL保证数据自动清理
     *
     * @param strategyId 策略ID
     * @param symbol     命中的股票代码
     */
    public void saveMatch(String strategyId, String symbol) {
        // 中文：构建按日期隔离的Key
        // English: Build date-isolated key
        String key = buildKey(strategyId, LocalDate.now());

        try {
            // 中文：使用SADD添加到Set，自动去重
            // English: Use SADD to add to Set, auto-dedupe
            stringRedisTemplate.opsForSet().add(key, symbol);

            // 中文：设置TTL，首次写入时设置过期时间
            // English: Set TTL on first write
            // 注意：每次都设置expire，Redis会更新过期时间，保证数据不会过早删除
            stringRedisTemplate.expire(key, Duration.ofHours(properties.getRedisTtlHours()));

            log.info("策略命中结果已保存|Strategy_match_saved,key={},symbol={}", key, symbol);
        } catch (Exception e) {
            // 中文：Redis写入失败不应影响主流程，仅记录日志
            // English: Redis write failure should not block main flow, just log
            log.error("策略命中结果保存失败|Strategy_match_save_failed,key={},symbol={}", key, symbol, e);
        }
    }

    /**
     * 批量保存策略命中记录
     *
     * @param strategyId 策略ID
     * @param symbols    命中的股票代码数组
     */
    public void saveMatchBatch(String strategyId, String... symbols) {
        if (symbols == null || symbols.length == 0) {
            return;
        }

        String key = buildKey(strategyId, LocalDate.now());

        try {
            // 中文：批量SADD，减少网络往返
            // English: Batch SADD to reduce network round trips
            stringRedisTemplate.opsForSet().add(key, symbols);
            stringRedisTemplate.expire(key, Duration.ofHours(properties.getRedisTtlHours()));

            log.info("策略命中结果批量保存|Strategy_match_batch_saved,key={},count={}", key, symbols.length);
        } catch (Exception e) {
            log.error("策略命中结果批量保存失败|Strategy_match_batch_save_failed,key={}", key, e);
        }
    }

    /**
     * 检查股票是否命中某策略
     *
     * @param strategyId 策略ID
     * @param symbol     股票代码
     * @return true-已命中，false-未命中
     */
    public boolean isMember(String strategyId, String symbol) {
        String key = buildKey(strategyId, LocalDate.now());
        Boolean result = stringRedisTemplate.opsForSet().isMember(key, symbol);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取策略命中数量
     *
     * @param strategyId 策略ID
     * @param date       日期
     * @return 命中股票数量
     */
    public long getMatchCount(String strategyId, LocalDate date) {
        String key = buildKey(strategyId, date);
        Long size = stringRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }

    /**
     * 构建Redis Key
     *
     * @param strategyId 策略ID
     * @param date       日期
     * @return 格式化的Key
     */
    private String buildKey(String strategyId, LocalDate date) {
        // 中文：Key格式：{prefix}:{strategyId}:{yyyyMMdd}
        // English: Key format: {prefix}:{strategyId}:{yyyyMMdd}
        return String.format("%s:%s:%s",
                properties.getRedisKeyPrefix(),
                strategyId,
                date.format(DATE_FORMATTER));
    }
}
