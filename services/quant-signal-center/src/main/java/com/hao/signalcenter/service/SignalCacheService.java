package com.hao.signalcenter.service;

import com.hao.signalcenter.model.StockSignal;
import constants.RedisKeyConstants;
import enums.strategy.SignalStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import util.JsonUtil;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 信号缓存服务 (Signal Cache Service)
 * <p>
 * 类职责：
 * 主动将通过风控的信号推送到 Redis 缓存，供股票列表模块快速读取。
 * <p>
 * 使用场景：
 * 信号落库后，调用此服务更新 Redis 缓存。
 * <p>
 * 设计目的：
 * 实现 Cache-Aside 写入策略，确保缓存数据实时更新。
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class SignalCacheService {

    /**
     * Redis Key 前缀：股票信号列表
     * 格式：stock:signal:list:{strategyId}:{tradeDate}
     */
    private static final String REDIS_KEY_PREFIX = RedisKeyConstants.STOCK_SIGNAL_LIST_PREFIX;

    /**
     * 缓存过期时间：24 小时
     */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 更新单个信号到缓存
     * <p>
     * 若信号状态为 PASSED，则添加到 Redis List 并设置过期时间。
     *
     * @param signal 信号实体
     */
    public void updateSignalCache(StockSignal signal) {
        if (signal == null || signal.getShowStatus() == null) {
            return;
        }

        // 只缓存通过的信号
        Integer showStatus = signal.getShowStatus();
        if (showStatus == null || SignalStatusEnum.PASSED.getCode() != showStatus) {
            log.debug("信号未通过_跳过缓存|Signal_not_passed_skip_cache,code={},status={}",
                    signal.getWindCode(), signal.getShowStatus());
            return;
        }

        String key = buildRedisKey(signal.getStrategyId(),
                signal.getTradeDate().format(DATE_FORMATTER));
        String value = JsonUtil.toJson(signal);

        try {
            // 使用 RPUSH 添加到列表尾部
            redisTemplate.opsForList().rightPush(key, value);
            // 设置过期时间
            redisTemplate.expire(key, CACHE_TTL);

            log.debug("信号缓存更新|Signal_cache_updated,key={},code={}",
                    key, signal.getWindCode());
        } catch (Exception e) {
            // 缓存更新失败不影响主流程
            log.warn("信号缓存更新失败|Signal_cache_update_failed,key={},error={}",
                    key, e.getMessage());
        }
    }

    /**
     * 批量刷新策略信号缓存
     * <p>
     * 将整个列表替换为新数据（先删后写）。
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日字符串
     * @param signals    信号列表
     */
    public void refreshSignalCache(String strategyId, String tradeDate, List<StockSignal> signals) {
        String key = buildRedisKey(strategyId, tradeDate);

        try {
            // 删除旧数据
            redisTemplate.delete(key);

            if (signals != null && !signals.isEmpty()) {
                // 批量添加新数据
                String[] values = signals.stream()
                        .map(JsonUtil::toJson)
                        .toArray(String[]::new);
                redisTemplate.opsForList().rightPushAll(key, values);
                // 设置过期时间
                redisTemplate.expire(key, CACHE_TTL);

                log.info("信号缓存刷新完成|Signal_cache_refreshed,key={},count={}",
                        key, signals.size());
            }
        } catch (Exception e) {
            log.error("信号缓存刷新失败|Signal_cache_refresh_failed,key={}", key, e);
        }
    }

    /**
     * 构建 Redis Key
     *
     * @param strategyId 策略ID
     * @param tradeDate  交易日字符串
     * @return Redis Key
     */
    private String buildRedisKey(String strategyId, String tradeDate) {
        return REDIS_KEY_PREFIX + strategyId + ":" + tradeDate;
    }
}
