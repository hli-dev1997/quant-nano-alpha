package com.hao.datacollector.service.impl;

import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.service.IndexPreCloseCacheService;
import com.hao.datacollector.service.QuotationService;
import constants.DateTimeFormatConstants;
import constants.RedisKeyConstants;
import enums.market.RiskMarketIndexEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指数昨收价缓存服务实现类
 * <p>
 * 负责从数据库加载各指数上一交易日收盘价，并缓存到 Redis。
 * 使用公共的 Redis Key 前缀 {@link RedisKeyConstants#RISK_INDEX_PRE_CLOSE_PREFIX}，
 * 供风控模块直接读取。
 * <p>
 * 核心逻辑:
 * <ol>
 *     <li>从 {@link RiskMarketIndexEnum} 获取所有需要缓存的指数代码</li>
 *     <li>根据回放配置确定目标交易日</li>
 *     <li>从交易日历获取上一交易日</li>
 *     <li>调用 {@link QuotationService#getIndexPreClosePrice} 查询收盘价</li>
 *     <li>将结果写入 Redis</li>
 * </ol>
 *
 * @author hli
 * @date 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexPreCloseCacheServiceImpl implements IndexPreCloseCacheService {

    private final QuotationService quotationService;
    private final StringRedisTemplate redisTemplate;
    private final ReplayProperties replayProperties;

    /**
     * 缓存过期时间：25小时（覆盖到下个交易日）
     */
    private static final Duration CACHE_TTL = Duration.ofHours(25);

    /**
     * 日期格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    @Override
    public int warmUpCache() {
        log.info("开始预热指数昨收价缓存|Start_warming_up_index_pre_close_cache");

        // 根据回放配置确定目标交易日
        LocalDate targetTradeDate;
        if (replayProperties.isEnabled() && replayProperties.getStartDate() != null) {
            // 回放模式：使用回放开始日期
            targetTradeDate = LocalDate.parse(replayProperties.getStartDate(), DATE_FORMATTER);
            log.info("回放模式启用，使用回放日期|Replay_mode_enabled,targetDate={}", targetTradeDate);
        } else {
            // 实盘模式：使用当前日期
            targetTradeDate = LocalDate.now();
            log.info("实盘模式，使用当前日期|Live_mode,targetDate={}", targetTradeDate);
        }

        return warmUpCacheForDate(targetTradeDate);
    }

    @Override
    public int warmUpCacheForDate(LocalDate targetTradeDate) {
        if (targetTradeDate == null) {
            log.error("目标交易日为空|Target_trade_date_is_null");
            return 0;
        }

        log.info("为指定日期预热指数昨收价缓存|Warm_up_cache_for_date,targetDate={}", targetTradeDate);

        // 1. 获取所有需要缓存的指数代码（从 RiskMarketIndexEnum 遍历）
        List<String> indexCodes = Arrays.stream(RiskMarketIndexEnum.values())
                .map(RiskMarketIndexEnum::getCode)
                .collect(Collectors.toList());

        log.info("指数代码列表|Index_codes,count={},codes={}", indexCodes.size(), indexCodes);

        // 2. 获取目标日期的上一交易日
        LocalDate previousTradeDate = getPreviousTradeDateFor(targetTradeDate);
        if (previousTradeDate == null) {
            log.error("无法获取上一交易日，缓存预热失败|Cannot_get_previous_trade_date,targetDate={}", targetTradeDate);
            return 0;
        }

        String tradeDateStr = previousTradeDate.format(DATE_FORMATTER);
        log.info("上一交易日|Previous_trade_date,targetDate={},previousDate={}", targetTradeDate, tradeDateStr);

        // 3. 从数据库查询各指数的收盘价
        List<HistoryTrendDTO> priceList = quotationService.getIndexPreClosePrice(tradeDateStr, indexCodes);
        if (priceList == null || priceList.isEmpty()) {
            log.warn("未查询到指数收盘价数据|No_index_pre_close_data_found,tradeDate={}", tradeDateStr);
            return 0;
        }

        // 4. 写入 Redis 缓存
        int cachedCount = 0;
        for (HistoryTrendDTO dto : priceList) {
            String windCode = dto.getWindCode();
            Double latestPrice = dto.getLatestPrice();

            if (windCode == null || latestPrice == null) {
                continue;
            }

            try {
                String key = buildRedisKey(windCode);
                redisTemplate.opsForValue().set(key, String.valueOf(latestPrice), CACHE_TTL);
                cachedCount++;
                log.debug("缓存指数昨收价|Cache_index_pre_close,windCode={},price={}", windCode, latestPrice);
            } catch (Exception e) {
                log.error("写入Redis缓存失败|Redis_cache_write_error,windCode={}", windCode, e);
            }
        }

        log.info("指数昨收价缓存预热完成|Index_pre_close_cache_warm_up_done,targetDate={},previousDate={},cachedCount={}/{}",
                targetTradeDate, tradeDateStr, cachedCount, indexCodes.size());

        return cachedCount;
    }

    @Override
    public Map<String, Double> getCachedPreClosePrices() {
        Map<String, Double> result = new HashMap<>();

        // 从 RiskMarketIndexEnum 获取所有指数代码
        for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
            String key = buildRedisKey(index.getCode());
            try {
                String value = redisTemplate.opsForValue().get(key);
                if (value != null) {
                    result.put(index.getCode(), Double.parseDouble(value));
                }
            } catch (Exception e) {
                log.warn("读取Redis缓存失败|Redis_cache_read_error,windCode={}", index.getCode(), e);
            }
        }

        return result;
    }

    @Override
    public void refreshCache(String windCode) {
        if (windCode == null || windCode.isEmpty()) {
            return;
        }

        // 先删除旧缓存
        String key = buildRedisKey(windCode);
        redisTemplate.delete(key);

        // 根据回放配置确定目标日期
        LocalDate targetDate = replayProperties.isEnabled() && replayProperties.getStartDate() != null
                ? LocalDate.parse(replayProperties.getStartDate(), DATE_FORMATTER)
                : LocalDate.now();

        // 获取上一交易日
        LocalDate previousTradeDate = getPreviousTradeDateFor(targetDate);
        if (previousTradeDate == null) {
            log.warn("无法获取上一交易日，刷新缓存失败|Cannot_get_previous_trade_date_for_refresh");
            return;
        }

        String tradeDateStr = previousTradeDate.format(DATE_FORMATTER);

        // 查询并缓存
        List<HistoryTrendDTO> priceList = quotationService.getIndexPreClosePrice(tradeDateStr, List.of(windCode));
        if (priceList != null && !priceList.isEmpty()) {
            Double latestPrice = priceList.get(0).getLatestPrice();
            if (latestPrice != null) {
                redisTemplate.opsForValue().set(key, String.valueOf(latestPrice), CACHE_TTL);
                log.info("刷新指数昨收价缓存|Refresh_index_pre_close_cache,windCode={},price={}", windCode, latestPrice);
            }
        }
    }

    @Override
    public void clearCache() {
        for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
            String key = buildRedisKey(index.getCode());
            redisTemplate.delete(key);
        }
        log.info("指数昨收价缓存已清空|Index_pre_close_cache_cleared");
    }

    /**
     * 构建 Redis Key
     *
     * @param windCode 指数代码
     * @return Redis Key
     */
    private String buildRedisKey(String windCode) {
        return RedisKeyConstants.RISK_INDEX_PRE_CLOSE_PREFIX + windCode;
    }

    /**
     * 获取指定日期的上一交易日
     * <p>
     * 根据目标日期年份选择对应的交易日历，查找上一个交易日。
     *
     * @param targetDate 目标日期
     * @return 上一交易日，如果无法确定则返回 null
     */
    private LocalDate getPreviousTradeDateFor(LocalDate targetDate) {
        if (targetDate == null) {
            return null;
        }

        // 根据年份选择交易日历
        List<LocalDate> tradeDateList = getTradeDateListForYear(targetDate.getYear());
        if (tradeDateList == null || tradeDateList.isEmpty()) {
            log.warn("交易日历为空，无法获取上一交易日|Trade_date_list_empty,year={}", targetDate.getYear());
            return null;
        }

        // 查找目标日期在列表中的位置
        int targetIndex = -1;
        for (int i = 0; i < tradeDateList.size(); i++) {
            if (tradeDateList.get(i).equals(targetDate)) {
                targetIndex = i;
                break;
            }
            // 如果目标日期不是交易日，找到第一个大于目标日期的交易日
            if (tradeDateList.get(i).isAfter(targetDate)) {
                targetIndex = i;
                break;
            }
        }

        // 返回前一个交易日
        if (targetIndex > 0) {
            return tradeDateList.get(targetIndex - 1);
        }

        // 如果目标日期晚于所有列表中的日期，返回最后一个交易日
        if (targetIndex == -1 && !tradeDateList.isEmpty()) {
            return tradeDateList.get(tradeDateList.size() - 1);
        }

        // 如果目标日期是年内第一个交易日，需要查上一年的最后一个交易日
        if (targetIndex == 0) {
            List<LocalDate> previousYearList = getTradeDateListForYear(targetDate.getYear() - 1);
            if (previousYearList != null && !previousYearList.isEmpty()) {
                return previousYearList.get(previousYearList.size() - 1);
            }
        }

        log.warn("无法确定上一交易日|Cannot_determine_previous_trade_date,targetDate={}", targetDate);
        return null;
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
                log.warn("不支持的年份交易日历|Unsupported_year_trade_date_list,year={}", year);
                yield null;
            }
        };
    }
}
