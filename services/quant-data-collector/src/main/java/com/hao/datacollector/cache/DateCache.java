package com.hao.datacollector.cache;

import constants.DateTimeFormatConstants;
import constants.RedisKeyConstants;
import util.DateUtil;
import com.hao.datacollector.service.BaseDataService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * 日期相关数据缓存
 *
 * 设计目的：
 * 1. 缓存年度交易日历，降低重复查询开销。
 * 2. 提供统一的交易日历访问入口。
 * 3. 将交易日历写入 Redis，供其他微服务（如策略引擎）读取。
 *
 * 为什么需要该类：
 * - 交易日历是多处依赖的基础数据，需集中管理与复用。
 *
 * 核心实现思路：
 * - 启动时预加载不同年份的交易日历并缓存到静态列表。
 * - 同时写入 Redis SortedSet，供跨服务共享。
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-06-14 17:34:21
 * @description: 日期相关数据缓存
 */
@Slf4j
@Component("DateCache")
public class DateCache {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
    private static final long REDIS_TTL_DAYS = 365; // 缓存一年

    // ==================== 全量交易日历（合并除 CurrentYearTradeDateList 外的所有年份）====================

    /**
     * 全量交易日历（2020年至今年，按日期升序排列）
     * <p>
     * 用于策略引擎等需要完整交易日历的场景。
     */
    public static List<LocalDate> AllTradeDateList;

    // ==================== 各年度交易日历 ====================

    /**
     * 今年整年交易日历
     */
    public static List<LocalDate> ThisYearTradeDateList;

    /**
     * 年初至今的交易日历（动态，不参与全量合并）
     */
    public static List<LocalDate> CurrentYearTradeDateList;

    /**
     * 2020年的交易日历
     */
    public static List<LocalDate> Year2020TradeDateList;

    /**
     * 2021年的交易日历
     */
    public static List<LocalDate> Year2021TradeDateList;

    /**
     * 2022年的交易日历
     */
    public static List<LocalDate> Year2022TradeDateList;

    /**
     * 2023年的交易日历
     */
    public static List<LocalDate> Year2023TradeDateList;

    /**
     * 2024年的交易日历
     */
    public static List<LocalDate> Year2024TradeDateList;

    /**
     * 2025年的交易日历
     */
    public static List<LocalDate> Year2025TradeDateList;


    @Autowired
    private BaseDataService baseDataService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 初始化交易日历缓存
     *
     * 实现逻辑：
     * 1. 计算年度起止日期。
     * 2. 按年份批量拉取交易日历并缓存。
     * 3. 合并所有年份到 AllTradeDateList。
     * 4. 同时写入 Redis 供其他服务读取。
     */
    @PostConstruct
    private void initDateList() {
        // 今年整年的交易日历
        String firstDayOfYear = DateUtil.getFirstDayOfYear(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        String lastDayOfYear = DateUtil.getLastDayOfYear(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        ThisYearTradeDateList = baseDataService.getTradeDateListByTime(firstDayOfYear, lastDayOfYear);
        
        // 年初至今的交易日历（动态）
        String currentDay = DateUtil.getCurrentDateTime(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        CurrentYearTradeDateList = baseDataService.getTradeDateListByTime(firstDayOfYear, currentDay);
        
        // 各历史年份的交易日历
        Year2020TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2020, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2020, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        Year2021TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2021, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2021, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        Year2022TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2022, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2022, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        Year2023TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2023, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2023, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        Year2024TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2024, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2024, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        Year2025TradeDateList = baseDataService.getTradeDateListByTime(
                DateUtil.getFirstDayOfYear(2025, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT),
                DateUtil.getLastDayOfYear(2025, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));

        // 合并所有年份到 AllTradeDateList（使用 TreeSet 去重并排序）
        buildAllTradeDateList();

        log.info("交易日历缓存完成|Trade_date_cache_loaded,allSize={},thisYearSize={},currentYearSize={}",
                AllTradeDateList.size(), ThisYearTradeDateList.size(), CurrentYearTradeDateList.size());

        // 将交易日历写入 Redis，供策略引擎等其他服务读取
        saveTradeDatesToRedis();
    }

    /**
     * 合并所有年份交易日历到 AllTradeDateList
     * <p>
     * 使用 TreeSet 自动去重和排序，包含 ThisYearTradeDateList 和所有历史年份。
     * 不包含 CurrentYearTradeDateList（因为是动态的年初至今数据）。
     */
    private void buildAllTradeDateList() {
        TreeSet<LocalDate> allDates = new TreeSet<>();
        
        // 添加今年整年
        if (ThisYearTradeDateList != null) {
            allDates.addAll(ThisYearTradeDateList);
        }
        // 添加各历史年份
        if (Year2020TradeDateList != null) {
            allDates.addAll(Year2020TradeDateList);
        }
        if (Year2021TradeDateList != null) {
            allDates.addAll(Year2021TradeDateList);
        }
        if (Year2022TradeDateList != null) {
            allDates.addAll(Year2022TradeDateList);
        }
        if (Year2023TradeDateList != null) {
            allDates.addAll(Year2023TradeDateList);
        }
        if (Year2024TradeDateList != null) {
            allDates.addAll(Year2024TradeDateList);
        }
        if (Year2025TradeDateList != null) {
            allDates.addAll(Year2025TradeDateList);
        }
        
        AllTradeDateList = new ArrayList<>(allDates);
        log.info("全量交易日历构建完成|All_trade_dates_built,count={}", AllTradeDateList.size());
    }

    /**
     * 将交易日历写入 Redis SortedSet
     * <p>
     * Key 格式: TRADE_DATE:CALENDAR:{年份} 或 TRADE_DATE:CALENDAR:ALL
     * Member: yyyyMMdd 格式的日期字符串
     * Score: yyyyMMdd 的数值（用于排序）
     */
    private void saveTradeDatesToRedis() {
        try {
            int currentYear = LocalDate.now().getYear();
            
            // 保存当年交易日历
            saveSingleYearToRedis(currentYear, ThisYearTradeDateList);
            
            // 保存历史年份（用于跨年数据查询）
            saveSingleYearToRedis(2020, Year2020TradeDateList);
            saveSingleYearToRedis(2021, Year2021TradeDateList);
            saveSingleYearToRedis(2022, Year2022TradeDateList);
            saveSingleYearToRedis(2023, Year2023TradeDateList);
            saveSingleYearToRedis(2024, Year2024TradeDateList);
            saveSingleYearToRedis(2025, Year2025TradeDateList);
            
            // 保存全量交易日历
            saveAllTradeDatesToRedis();
            
            log.info("交易日历已写入Redis|Trade_dates_saved_to_redis,years=[2020-{}],allCount={}",
                    currentYear, AllTradeDateList.size());
        } catch (Exception e) {
            log.error("交易日历写入Redis失败|Trade_dates_save_to_redis_failed", e);
        }
    }

    /**
     * 保存单个年份的交易日历到 Redis
     */
    private void saveSingleYearToRedis(int year, List<LocalDate> tradeDates) {
        if (tradeDates == null || tradeDates.isEmpty()) {
            return;
        }
        
        String redisKey = RedisKeyConstants.TRADE_DATE_CALENDAR_PREFIX + year;
        
        // 先清除旧数据
        stringRedisTemplate.delete(redisKey);
        
        // 批量写入 SortedSet
        for (LocalDate date : tradeDates) {
            String dateStr = date.format(DATE_FORMATTER);
            double score = Double.parseDouble(dateStr);
            stringRedisTemplate.opsForZSet().add(redisKey, dateStr, score);
        }
        
        // 设置过期时间
        stringRedisTemplate.expire(redisKey, REDIS_TTL_DAYS, TimeUnit.DAYS);
        
        log.debug("交易日历年份写入Redis|Trade_dates_year_saved,year={},count={}", year, tradeDates.size());
    }

    /**
     * 保存全量交易日历到 Redis
     */
    private void saveAllTradeDatesToRedis() {
        if (AllTradeDateList == null || AllTradeDateList.isEmpty()) {
            return;
        }
        
        String redisKey = RedisKeyConstants.TRADE_DATE_CALENDAR_ALL;
        
        // 先清除旧数据
        stringRedisTemplate.delete(redisKey);
        
        // 批量写入 SortedSet
        for (LocalDate date : AllTradeDateList) {
            String dateStr = date.format(DATE_FORMATTER);
            double score = Double.parseDouble(dateStr);
            stringRedisTemplate.opsForZSet().add(redisKey, dateStr, score);
        }
        
        // 设置过期时间
        stringRedisTemplate.expire(redisKey, REDIS_TTL_DAYS, TimeUnit.DAYS);
        
        log.debug("全量交易日历写入Redis|All_trade_dates_saved,count={}", AllTradeDateList.size());
    }
}
