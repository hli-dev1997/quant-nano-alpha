package com.hao.datacollector.service.impl;

import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.cache.StockCache;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import com.hao.datacollector.service.StrategyPreparationService;
import constants.DateTimeFormatConstants;
import dto.ClosePriceDTO;
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
 * │  3. 提取每日收盘价 → 组织为List<ClosePriceDTO>              │
 * │  4. 存入Redis Hash → Key: NINE_TURN:PREHEAT:{yyyyMMdd}      │
 * │                     Field: windCode                         │
 * │                     Value: [{tradeDate, closePrice}, ...]   │
 * └─────────────────────────────────────────────────────────────┘
 * <p>
 * Redis数据结构：
 * - Key: NINE_TURN:PREHEAT:20260102
 * - Hash Field: 600519.SH
 * - Hash Value: [{"tradeDate":"20260101","closePrice":1800.5}, ...]
 * <p>
 * 设计原因：
 * - 使用Hash结构，单个Key存储所有股票，减少Redis Key数量。
 * - 收盘价按时间倒序排列（0=昨日），与策略引擎RingBuffer设计一致。
 * - 使用ClosePriceDTO包含日期信息，便于调试和追踪。
 * - closePrice为null表示停牌/数据异常，策略端应跳过。
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyPreparationServiceImpl implements StrategyPreparationService {

    /**
     * 使用枚举统一管理九转策略的Redis配置（红九、绿九共用）
     */
    private static final StrategyRedisKeyEnum NINE_TURN_CONFIG = StrategyRedisKeyEnum.NINE_TURN_PREHEAT;

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
        // 无参方法委托给带参方法，使用全量股票
        return prepareNineTurnData(tradeDate, null);
    }

    @Override
    public int prepareNineTurnData(LocalDate tradeDate, List<String> stockCodes) {
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

        // 优先使用传入的 stockCodes，为空时回退到 StockCache.allWindCode
        List<String> targetStockCodes = (stockCodes != null && !stockCodes.isEmpty())
                ? stockCodes
                : StockCache.allWindCode;
        log.info("九转预热_目标股票|Nine_turn_preheat_target_stocks,count={},source={}",
                targetStockCodes.size(), (stockCodes != null && !stockCodes.isEmpty()) ? "params" : "StockCache");

        // 优化：使用专门的轻量级查询接口，只获取每日收盘价
        List<HistoryTrendDTO> historyData = quotationService.getDailyClosePriceByStockList(startDate, endDate, targetStockCodes);

        log.info("九转预热_查询历史数据|Nine_turn_preheat_query_history,startDate={},endDate={},recordCount={}", startDate, endDate, historyData.size());
        if (historyData.isEmpty()) {
            log.warn("九转预热_历史数据为空|Nine_turn_preheat_no_data,startDate={},endDate={}", startDate, endDate);
            return 0;
        }

        // 实现思路：
        // Step 4: 按股票代码分组，提取每日收盘价,0号位数值是上一个交易日收盘价
        // 使用 ClosePriceDTO 包含交易日期，便于调试和追踪；closePrice为null表示停牌/异常
        Map<String, List<ClosePriceDTO>> stockClosePrices = extractClosingPrices(historyData, tradeDates);

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
     * 1. 根据日期年份动态选择交易日历。
     * 2. 检查tradeDate是否在该年交易日历中。
     * 3. 不在则抛出异常（可能是非交易日）。
     *
     * @param tradeDate 待校验的交易日
     */
    private void validateTradeDate(LocalDate tradeDate) {
        // 根据日期年份动态获取交易日历
        List<LocalDate> tradeDates = getTradeDateListForYear(tradeDate.getYear());

        if (tradeDates == null || tradeDates.isEmpty()) {
            throw new IllegalStateException(
                    String.format("交易日历未初始化|Trade_date_list_not_initialized,year=%d", tradeDate.getYear()));
        }

        if (!tradeDates.contains(tradeDate)) {
            throw new IllegalArgumentException(
                    String.format("非交易日|Invalid_trade_date,date=%s,year=%d", tradeDate, tradeDate.getYear()));
        }
    }

    /**
     * 获取前N个交易日列表
     * <p>
     * 实现逻辑：
     * 1. 根据日期年份动态选择交易日历。
     * 2. 从交易日历中找到tradeDate的位置。
     * 3. 向前取N个交易日（不包括当天）。
     *
     * @param tradeDate   当前交易日
     * @param historyDays 需要的历史天数
     * @return 前N个交易日列表（按时间倒序，0=昨日）
     */
    private List<LocalDate> getLastNTradeDates(LocalDate tradeDate, int historyDays) {
        // 根据日期年份动态获取交易日历
        List<LocalDate> allTradeDates = getTradeDateListForYear(tradeDate.getYear());

        // 找到当前交易日在列表中的位置
        int currentIndex = allTradeDates.indexOf(tradeDate);

        if (currentIndex < historyDays) {
            // 如果今年交易日不足，需要跨年处理（暂不支持）
            throw new IllegalArgumentException(
                    String.format("年初交易日不足|Insufficient_trade_days,tradeDate=%s,required=%d,available=%d",
                            tradeDate, historyDays, currentIndex));
        }

        // 获取前N个交易日（不包含当天）
        List<LocalDate> lastNDates = new ArrayList<>();
        for (int i = 1; i <= historyDays; i++) {
            lastNDates.add(allTradeDates.get(currentIndex - i));
        }

        // lastNDates 已经是按时间倒序（0=昨日，1=前日...）
        return lastNDates;
    }

    /**
     * 根据年份获取交易日历
     *
     * @param year 年份
     * @return 该年的交易日历列表
     */
    private List<LocalDate> getTradeDateListForYear(int year) {
        return switch (year) {
            case 2020 -> DateCache.Year2020TradeDateList;
            case 2021 -> DateCache.Year2021TradeDateList;
            case 2022 -> DateCache.Year2022TradeDateList;
            case 2023 -> DateCache.Year2023TradeDateList;
            case 2024 -> DateCache.Year2024TradeDateList;
            case 2025 -> DateCache.Year2025TradeDateList;
            default -> {
                // 当前年份使用 ThisYearTradeDateList
                if (year == LocalDate.now().getYear()) {
                    yield DateCache.ThisYearTradeDateList;
                }
                throw new IllegalArgumentException(
                        String.format("不支持的年份交易日历|Unsupported_year_trade_date_list,year=%d", year));
            }
        };
    }

    /**
     * 从历史分时数据中提取每日收盘价
     * <p>
     * 实现逻辑：
     * 1. 按股票代码分组。
     * 2. 对每只股票，按交易日再分组。
     * 3. 取每日最后一条记录的latestPrice作为收盘价。
     * 4. 按tradeDates顺序组织为List<ClosePriceDTO>。
     * <p>
     * <b>null 值说明：</b>
     * 当某交易日数据不可用（停牌/数据缺失）时，closePrice 为 null。
     * 策略端应跳过 closePrice 为 null 的记录，向前取上一个有效交易日的数据。
     *
     * @param historyData 历史分时数据
     * @param tradeDates  交易日列表（按时间倒序）
     * @return Map<windCode, List<ClosePriceDTO>>
     */
    private Map<String, List<ClosePriceDTO>> extractClosingPrices(
            List<HistoryTrendDTO> historyData,
            List<LocalDate> tradeDates) {

        // 日期格式化器
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DateTimeFormatConstants.DEFAULT_DATETIME_FORMAT);

        // 按股票代码分组
        Map<String, List<HistoryTrendDTO>> stockDataMap = historyData.stream()
                .collect(Collectors.groupingBy(HistoryTrendDTO::getWindCode));

        Map<String, List<ClosePriceDTO>> result = new HashMap<>();

        for (Map.Entry<String, List<HistoryTrendDTO>> entry : stockDataMap.entrySet()) {
            String windCode = entry.getKey();
            List<HistoryTrendDTO> stockData = entry.getValue();

            // 按交易日分组，取每日最后一条数据（保留完整 LocalDateTime 和 price）
            Map<LocalDate, HistoryTrendDTO> dailyLastRecord = stockData.stream()
                    .collect(Collectors.groupingBy(
                            dto -> dto.getTradeDate().toLocalDate(),
                            Collectors.collectingAndThen(
                                    Collectors.maxBy(Comparator.comparing(HistoryTrendDTO::getTradeDate)),
                                    opt -> opt.orElse(null)
                            )
                    ));

            // 按tradeDates顺序组织收盘价列表
            List<ClosePriceDTO> closePrices = new ArrayList<>();
            for (LocalDate date : tradeDates) {
                HistoryTrendDTO lastRecord = dailyLastRecord.get(date);
                String dateStr;
                Double price;
                if (lastRecord != null) {
                    // 使用完整的 LocalDateTime 格式化（保留时分秒）
                    dateStr = lastRecord.getTradeDate().format(dateFormatter);
                    price = lastRecord.getLatestPrice();
                } else {
                    // 无数据时使用日期的默认时间 00:00:00
                    dateStr = date.atStartOfDay().format(dateFormatter);
                    price = null;
                }
                
                // 使用 ClosePriceDTO，price 为 null 表示停牌/数据异常
                closePrices.add(new ClosePriceDTO(dateStr, price));

                // 调试日志：如果发现缺失数据，打印警告
                if (price == null && log.isDebugEnabled() && Math.random() < 0.001) {
                    log.debug("数据缺失_停牌或异常|Missing_data_suspended_or_error,code={},date={}",
                            windCode, dateStr);
                }
            }

            // 只保存有足够有效数据的股票（至少一半有效）
            long validCount = closePrices.stream()
                    .filter(dto -> dto.getClosePrice() != null)
                    .count();
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
     * <p>
     * Redis Value 格式：
     * [{"tradeDate":"20260120","closePrice":1850.5}, {"tradeDate":"20260119","closePrice":null}, ...]
     * closePrice 为 null 表示该交易日停牌或数据异常。
     *
     * @param tradeDate        交易日
     * @param stockClosePrices 股票收盘价数据（List<ClosePriceDTO>）
     * @return 保存的股票数量
     */
    private int saveToRedis(LocalDate tradeDate, Map<String, List<ClosePriceDTO>> stockClosePrices) {
        // 使用枚举构建完整的Redis Key
        String dateSuffix = tradeDate.format(DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        String redisKey = NINE_TURN_CONFIG.buildKey(dateSuffix);

        Map<String, String> hashMap = new HashMap<>();

        for (Map.Entry<String, List<ClosePriceDTO>> entry : stockClosePrices.entrySet()) {
            // 使用JsonUtil将List<ClosePriceDTO>序列化为JSON数组字符串
            String jsonValue = JsonUtil.toJson(entry.getValue());
            hashMap.put(entry.getKey(), jsonValue);
        }

        if (!hashMap.isEmpty()) {
            // 批量写入Redis Hash
            stringRedisTemplate.opsForHash().putAll(redisKey, hashMap);

            // 使用枚举配置的TTL
            stringRedisTemplate.expire(redisKey, NINE_TURN_CONFIG.getTtlHours(), TimeUnit.HOURS);

            log.info("九转预热_Redis写入完成|Nine_turn_preheat_redis_saved,key={},fieldCount={}",
                    redisKey, hashMap.size());
        }

        return hashMap.size();
    }
}
