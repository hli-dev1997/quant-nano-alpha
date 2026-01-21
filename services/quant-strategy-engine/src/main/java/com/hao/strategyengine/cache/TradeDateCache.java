package com.hao.strategyengine.cache;

import constants.DateTimeFormatConstants;
import constants.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 交易日历缓存 (策略引擎模块)
 *
 * 设计目的：
 * 1. 从 Redis 加载交易日历，供策略判断使用。
 * 2. 与 data-collector 模块解耦，通过 Redis 共享数据。
 *
 * 数据来源：
 * - data-collector 模块在启动时将交易日历写入 Redis
 * - Key: TRADE_DATE:CALENDAR:ALL（全量交易日历）
 * - Value 类型: SortedSet，score 为 yyyyMMdd 数值
 *
 * @author hli
 * @date 2026-01-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeDateCache {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 全量交易日历缓存（懒加载）
     */
    private volatile List<LocalDate> allTradeDates;

    /**
     * 获取全量交易日历
     * <p>
     * 从 Redis 加载 TRADE_DATE:CALENDAR:ALL，包含 2020 年至今的所有交易日。
     * <p>
     * 注意：如果 Redis 无数据，不会缓存空结果，下次调用时会重新尝试加载。
     *
     * @return 全量交易日列表（升序），如果 Redis 无数据返回空列表（非 null）
     */
    public List<LocalDate> getAllTradeDates() {
        // 如果已有缓存且非空，直接返回
        if (allTradeDates != null && !allTradeDates.isEmpty()) {
            return allTradeDates;
        }
        
        synchronized (this) {
            // 双重检查
            if (allTradeDates != null && !allTradeDates.isEmpty()) {
                return allTradeDates;
            }
            
            List<LocalDate> loaded = loadAllTradeDatesFromRedis();
            // 只有加载到数据才缓存，避免缓存空结果
            if (!loaded.isEmpty()) {
                allTradeDates = loaded;
            }
            return loaded;
        }
    }

    /**
     * 获取指定年份的交易日历
     *
     * @param year 年份
     * @return 交易日列表（升序）
     */
    public List<LocalDate> getTradeDatesByYear(int year) {
        return loadTradeDatesFromRedis(RedisKeyConstants.TRADE_DATE_CALENDAR_PREFIX + year);
    }

    /**
     * 从 Redis 加载全量交易日历
     */
    private List<LocalDate> loadAllTradeDatesFromRedis() {
        return loadTradeDatesFromRedis(RedisKeyConstants.TRADE_DATE_CALENDAR_ALL);
    }

    /**
     * 从 Redis 加载交易日历
     */
    private List<LocalDate> loadTradeDatesFromRedis(String redisKey) {
        try {
            Set<String> members = stringRedisTemplate.opsForZSet().range(redisKey, 0, -1);
            if (members == null || members.isEmpty()) {
                log.warn("Redis中无交易日历数据|No_trade_dates_in_redis,key={}", redisKey);
                return Collections.emptyList();
            }

            List<LocalDate> dates = new ArrayList<>();
            for (String dateStr : members) {
                try {
                    dates.add(LocalDate.parse(dateStr, DATE_FORMATTER));
                } catch (Exception e) {
                    log.warn("解析交易日期失败|Parse_trade_date_failed,dateStr={}", dateStr);
                }
            }

            log.info("交易日历加载完成|Trade_dates_loaded,key={},count={}", redisKey, dates.size());
            return dates;
        } catch (Exception e) {
            log.error("从Redis加载交易日历失败|Load_trade_dates_from_redis_failed,key={}", redisKey, e);
            return Collections.emptyList();
        }
    }

    /**
     * 刷新缓存
     */
    public void refresh() {
        synchronized (this) {
            allTradeDates = null;
            log.info("交易日历缓存已刷新|Trade_date_cache_refreshed");
        }
    }
}
