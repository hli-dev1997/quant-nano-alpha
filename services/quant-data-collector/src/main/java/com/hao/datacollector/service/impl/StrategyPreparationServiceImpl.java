package com.hao.datacollector.service.impl;

import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.cache.StockCache;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import com.hao.datacollector.service.StrategyPreparationService;
import constants.DateTimeFormatConstants;
import enums.strategy.StrategyRedisKeyEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import util.JsonUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 策略数据预处理服务实现类
 * <p>
 * 设计目的：
 * 1. 为九转序列策略预热历史收盘价数据。
 * 2. 将数据按策略规范组织并存入Redis，供策略引擎消费。
 * <p>
 * 核心实现思路：
 * ┌─────────────────────────────────────────────────────────────┐
 * │  数据流转：                                                  │
 * │  1. 校验交易日 → 获取前20个交易日日期列表                     │
 * │  2. 批量查询历史分时 → 按股票分组                            │
 * │  3. 提取每日收盘价 → 组织为List<Double>                      │
 * │  4. 存入Redis Hash → Key: NINE_TURN:PREHEAT:{yyyyMMdd}      │
 * │                     Field: windCode                         │
 * │                     Value: [price0, price1, ...]            │
 * └─────────────────────────────────────────────────────────────┘
 * <p>
 * Redis数据结构：
 * - Key: NINE_TURN:PREHEAT:20260102
 * - Hash Field: 600519.SH
 * - Hash Value: "[1800.5, 1795.0, 1802.3, ...]" (JSON数组)
 * <p>
 * 设计原因：
 * - 使用Hash结构，单个Key存储所有股票，减少Redis Key数量。
 * - 收盘价按时间倒序排列（0=昨日），与策略引擎RingBuffer设计一致。
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyPreparationServiceImpl implements StrategyPreparationService {

    /**
     * 使用枚举统一管理九转策略的Redis配置
     */
    private static final StrategyRedisKeyEnum NINE_TURN_CONFIG = StrategyRedisKeyEnum.NINE_TURN_RED_PREHEAT;

    private final QuotationService quotationService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 预热九转序列策略所需的历史数据
     * <p>
     * 实现逻辑：
     * 1. 校验tradeDate是否在CurrentYearTradeDateList中。
     * 2. 从交易日历中获取前N个交易日（N由枚举配置）。
     * 3. 调用QuotationService查询历史分时数据。
     * 4. 按股票分组，提取每日收盘价（当日最后一条）。
     * 5. 存入Redis Hash。
     *
     * @param tradeDate 当前交易日
     * @return 预热成功的股票数量
     */
    @Override
    public int prepareNineTurnData(LocalDate tradeDate) {
        // 实现思路：
        // Step 1: 校验交易日有效性
        validateTradeDate(tradeDate);
        // 实现思路：
        // Step 2: 获取前N个交易日列表（N由枚举配置）
        List<LocalDate> tradeDates = getLastNTradeDates(tradeDate, NINE_TURN_CONFIG.getHistoryDays());
        log.info("九转预热_获取交易日列表|Nine_turn_preheat_get_trade_dates,tradeDate={},dateCount={}",
                tradeDate, tradeDates.size());
        // 实现思路：
        // Step 3: 查询历史分时数据
        String startDate = tradeDates.getLast().format(DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        // Fix: 使用当前tradeDate作为结束日期，确保覆盖到tradeDates中的最近一天（昨日）。
        String endDate = tradeDate.format(DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));

        // 优化：使用专门的轻量级查询接口，只获取每日收盘价
        List<HistoryTrendDTO> historyData = quotationService.getDailyClosePriceByStockList(startDate, endDate, StockCache.allWindCode);

        log.info("九转预热_查询历史数据|Nine_turn_preheat_query_history,startDate={},endDate={},recordCount={}", startDate, endDate, historyData.size());
        if (historyData.isEmpty()) {
            log.warn("九转预热_历史数据为空|Nine_turn_preheat_no_data,startDate={},endDate={}", startDate, endDate);
            return 0;
        }

        // 实现思路：
        // Step 4: 按股票代码分组，提取每日收盘价,0号位数值是上一个交易日收盘价
        Map<String, List<Double>> stockClosePrices = extractClosingPrices(historyData, tradeDates);

        // 实现思路：
        // Step 5: 存入Redis Hash
        int savedCount = saveToRedis(tradeDate, stockClosePrices);

        log.info("九转预热完成|Nine_turn_preheat_complete,tradeDate={},stockCount={},savedCount={}",
                tradeDate, stockClosePrices.size(), savedCount);

        return savedCount;
    }

    /**
     * 校验交易日有效性
     * <p>
     * 实现逻辑：
     * 1. 检查tradeDate是否在CurrentYearTradeDateList中。
     * 2. 不在则抛出异常（可能是非交易日或跨年）。
     *
     * @param tradeDate 待校验的交易日
     */
    private void validateTradeDate(LocalDate tradeDate) {
        // 中文：从DateCache获取年初至今交易日列表
        // English: Get year-to-date trade date list from DateCache
        List<LocalDate> currentYearTradeDates = DateCache.Year2025TradeDateList;

        if (currentYearTradeDates == null || currentYearTradeDates.isEmpty()) {
            throw new IllegalStateException("交易日历未初始化|Trade_date_list_not_initialized");
        }

        if (!currentYearTradeDates.contains(tradeDate)) {
            throw new IllegalArgumentException(
                    String.format("非交易日或跨年|Invalid_trade_date,date=%s,reason=不在CurrentYearTradeDateList中", tradeDate));
        }
    }

    /**
     * 获取前N个交易日列表
     * <p>
     * 实现逻辑：
     * 1. 从交易日历中找到tradeDate的位置。
     * 2. 向前取N个交易日（不包括当天）。
     *
     * @param tradeDate   当前交易日
     * @param historyDays 需要的历史天数
     * @return 前N个交易日列表（按时间倒序，0=昨日）
     */
    private List<LocalDate> getLastNTradeDates(LocalDate tradeDate, int historyDays) {
        List<LocalDate> allTradeDates = DateCache.Year2025TradeDateList;

        // 中文：找到当前交易日在列表中的位置
        // English: Find current trade date position in list
        int currentIndex = allTradeDates.indexOf(tradeDate);

        if (currentIndex < historyDays) {
            // 中文：如果今年交易日不足，需要跨年处理（暂不支持）
            // English: If not enough days this year, need cross-year handling (not supported yet)
            throw new IllegalArgumentException(
                    String.format("年初交易日不足|Insufficient_trade_days,tradeDate=%s,required=%d,available=%d",
                            tradeDate, historyDays, currentIndex));
        }

        // 中文：获取前N个交易日（不包含当天）
        // English: Get last N trade dates (excluding today)
        List<LocalDate> lastNDates = new ArrayList<>();
        for (int i = 1; i <= historyDays; i++) {
            lastNDates.add(allTradeDates.get(currentIndex - i));
        }

        // 中文：lastNDates已经是按时间倒序（0=昨日，1=前日...）
        // English: lastNDates is already in reverse chronological order (0=yesterday, 1=day before...)
        return lastNDates;
    }

    /**
     * 从历史分时数据中提取每日收盘价
     * <p>
     * 实现逻辑：
     * 1. 按股票代码分组。
     * 2. 对每只股票，按交易日再分组。
     * 3. 取每日最后一条记录的latestPrice作为收盘价。
     * 4. 按tradeDates顺序组织为List<Double>。
     *
     * @param historyData 历史分时数据
     * @param tradeDates  交易日列表（按时间倒序）
     * @return Map<windCode, List < Double>>
     */
    private Map<String, List<Double>> extractClosingPrices(
            List<HistoryTrendDTO> historyData,
            List<LocalDate> tradeDates) {

        // 中文：按股票代码分组
        // English: Group by stock code
        Map<String, List<HistoryTrendDTO>> stockDataMap = historyData.stream()
                .collect(Collectors.groupingBy(HistoryTrendDTO::getWindCode));

        Map<String, List<Double>> result = new HashMap<>();

        for (Map.Entry<String, List<HistoryTrendDTO>> entry : stockDataMap.entrySet()) {
            String windCode = entry.getKey();
            List<HistoryTrendDTO> stockData = entry.getValue();

            // 中文：按交易日分组，取每日最后一条的latestPrice
            // English: Group by trade date, get last record's latestPrice for each day
            Map<LocalDate, Double> dailyClosePrices = stockData.stream()
                    .collect(Collectors.groupingBy(
                            dto -> dto.getTradeDate().toLocalDate(),
                            Collectors.collectingAndThen(
                                    Collectors.maxBy(Comparator.comparing(HistoryTrendDTO::getTradeDate)),
                                    opt -> opt.map(HistoryTrendDTO::getLatestPrice).orElse(null)
                            )
                    ));

            // 中文：按tradeDates顺序组织收盘价列表
            // English: Organize closing prices by tradeDates order
            List<Double> closePrices = new ArrayList<>();
            for (LocalDate date : tradeDates) {
                Double price = dailyClosePrices.get(date);
                if (price != null) {
                    closePrices.add(price);
                } else {
                    // 中文：缺失数据用NaN填充
                    // English: Fill missing data with NaN
                    closePrices.add(Double.NaN);

                    // 调试日志：如果发现缺失数据，打印警告（仅打印一次或少量）
                    if (log.isDebugEnabled() && Math.random() < 0.0001) {
                        log.debug("数据缺失警告|Missing_data_warning,code={},date={},availableDates={}",
                                windCode, date, dailyClosePrices.keySet());
                    }
                }
            }

            // 中文：只保存有足够数据的股票
            // English: Only save stocks with enough data
            long validCount = closePrices.stream().filter(p -> !Double.isNaN(p)).count();
            if (validCount >= NINE_TURN_CONFIG.getHistoryDays() / 2) {
                result.put(windCode, closePrices);
            }
        }

        return result;
    }

    /**
     * 保存数据到Redis Hash
     * <p>
     * 实现逻辑：
     * 1. 使用枚举构建Redis Key
     * 2. 遍历stockClosePrices，将每只股票的收盘价列表序列化为JSON。
     * 3. 批量写入Redis Hash。
     * 4. 使用枚举配置的TTL。
     *
     * @param tradeDate        交易日
     * @param stockClosePrices 股票收盘价数据
     * @return 保存的股票数量
     */
    private int saveToRedis(LocalDate tradeDate, Map<String, List<Double>> stockClosePrices) {
        // 中文：使用枚举构建完整的Redis Key
        // English: Build complete Redis Key using enum
        String dateSuffix = tradeDate.format(DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        String redisKey = NINE_TURN_CONFIG.buildKey(dateSuffix);

        Map<String, String> hashMap = new HashMap<>();

        for (Map.Entry<String, List<Double>> entry : stockClosePrices.entrySet()) {
            // 中文：使用JsonUtil将List<Double>序列化为JSON数组字符串
            // English: Use JsonUtil to serialize List<Double> to JSON array string
            String jsonValue = JsonUtil.toJson(entry.getValue());
            hashMap.put(entry.getKey(), jsonValue);
        }

        if (!hashMap.isEmpty()) {
            // 中文：批量写入Redis Hash
            // English: Batch write to Redis Hash
            stringRedisTemplate.opsForHash().putAll(redisKey, hashMap);

            // 中文：使用枚举配置的TTL
            // English: Use TTL from enum config
            stringRedisTemplate.expire(redisKey, NINE_TURN_CONFIG.getTtlHours(), TimeUnit.HOURS);

            log.info("九转预热_Redis写入完成|Nine_turn_preheat_redis_saved,key={},fieldCount={}",
                    redisKey, hashMap.size());
        }

        return hashMap.size();
    }
}
