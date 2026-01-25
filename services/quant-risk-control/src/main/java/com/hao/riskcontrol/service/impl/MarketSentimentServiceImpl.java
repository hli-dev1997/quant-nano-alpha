package com.hao.riskcontrol.service.impl;

import com.hao.riskcontrol.common.enums.market.MarketSentimentScorer;
import com.hao.riskcontrol.common.enums.market.ScoreZone;
import com.hao.riskcontrol.dto.quotation.IndexQuotationDTO;
import com.hao.riskcontrol.service.MarketSentimentService;
import constants.RedisKeyConstants;
import enums.market.RiskMarketIndexEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 市场情绪评分服务实现类
 * <p>
 * 核心职责：
 * <ol>
 *     <li>缓存各指数的实时价格（内存 ConcurrentHashMap）</li>
 *     <li>缓存各指数当天第一条行情作为基准价（替代 Redis 昨收价）</li>
 *     <li>调用 RiskMarketIndexEnum 计算综合涨跌幅</li>
 *     <li>调用 MarketSentimentScorer 评估风险区间</li>
 * </ol>
 *
 * @author hli
 * @date 2026-01-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSentimentServiceImpl implements MarketSentimentService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 当前各指数实时价格缓存
     * Key: 指数代码（如 000300.SH）
     * Value: 最新价格
     */
    private final ConcurrentHashMap<String, Double> currentPriceMap = new ConcurrentHashMap<>();

    /**
     * 各指数当天第一条行情价格缓存（作为基准价）
     * Key: 指数代码（如 000300.SH）
     * Value: 当天第一条行情的价格
     */
    private final ConcurrentHashMap<String, Double> firstPriceMap = new ConcurrentHashMap<>();

    /**
     * 当前缓存的交易日期（用于判断跨日）
     */
    private volatile LocalDate currentTradeDate;

    /**
     * 最后更新时间
     */
    private volatile LocalDateTime lastUpdateTime;

    @Override
    public void updateIndexPrice(IndexQuotationDTO dto) {
        if (dto == null || dto.getWindCode() == null || dto.getLatestPrice() == null) {
            log.warn("更新指数价格参数无效|Update_index_price_invalid,dto={}", dto);
            return;
        }

        LocalDate tradeDate = dto.getTradeDate() != null ? dto.getTradeDate().toLocalDate() : LocalDate.now();
        String windCode = dto.getWindCode();
        Double latestPrice = dto.getLatestPrice();

        // 判断是否跨交易日，如果是则清空缓存
        if (currentTradeDate == null || !currentTradeDate.equals(tradeDate)) {
            log.info("检测到新交易日|New_trade_date_detected,oldDate={},newDate={}", currentTradeDate, tradeDate);
            firstPriceMap.clear();
            currentPriceMap.clear();
            currentTradeDate = tradeDate;
        }

        // 缓存当天第一条行情作为基准价（只存第一条）
        if (!firstPriceMap.containsKey(windCode)) {
            firstPriceMap.put(windCode, latestPrice);
            log.info("第一条指数行情已缓存|First_index_price_cached,windCode={},price={},date={},totalCached={}",
                    windCode, latestPrice, tradeDate, firstPriceMap.size());
        }

        // 更新当前实时价格
        currentPriceMap.put(windCode, latestPrice);
        lastUpdateTime = dto.getTradeDate();

        // 计算涨跌幅并输出
        Double basePrice = firstPriceMap.get(windCode);
        if (basePrice != null && basePrice > 0) {
            double changePercent = (latestPrice - basePrice) / basePrice * 100;
            log.info("指数行情更新|Index_update,windCode={},base={},current={},change={}",
                    windCode, String.format("%.2f", basePrice), 
                    String.format("%.2f", latestPrice), 
                    String.format("%.2f%%", changePercent));
        }
    }

    @Override
    public MarketSentimentScorer.EvaluationResult getCurrentEvaluation() {
        int compositeScore = calculateCurrentScore();
        return MarketSentimentScorer.evaluateWithDetails(compositeScore);
    }

    @Override
    public int getCurrentCompositeScore() {
        return calculateCurrentScore();
    }

    @Override
    public void reset() {
        currentPriceMap.clear();
        firstPriceMap.clear();
        currentTradeDate = null;
        lastUpdateTime = null;
        log.info("市场情绪服务已重置|Market_sentiment_service_reset");
    }

    /**
     * 计算当前综合评分
     * <p>
     * 1. 获取各指数基准价（优先内存缓存，降级到 Redis）
     * 2. 获取各指数当前价（从内存缓存）
     * 3. 调用 RiskMarketIndexEnum.calculateCompositeScore 计算
     *
     * @return 综合评分（整数基点）
     */
    private int calculateCurrentScore() {
        if (currentPriceMap.isEmpty()) {
            log.info("当前无指数价格数据，返回0|No_index_price_data");
            return 0;
        }

        LocalDate today = currentTradeDate != null ? currentTradeDate : LocalDate.now();

        // 构建当前价格 Map
        Map<String, Double> indexPriceMap = new HashMap<>(currentPriceMap);

        // 获取基准价 Map（优先使用内存缓存的第一条价格）
        Map<String, Double> indexPreCloseMap = new HashMap<>();
        for (String windCode : indexPriceMap.keySet()) {
            Double basePrice = getBasePrice(windCode);
            if (basePrice != null && basePrice > 0) {
                indexPreCloseMap.put(windCode, basePrice);
            }
        }

        if (indexPreCloseMap.isEmpty()) {
            log.warn("无法获取基准价数据，返回0|No_base_price_data");
            return 0;
        }

        log.info("开始计算综合评分|Calc_score_start,date={},indexCount={},basePriceCount={}",
                today, indexPriceMap.size(), indexPreCloseMap.size());

        // 调用核心计算方法
        int score = RiskMarketIndexEnum.calculateCompositeScore(today, indexPriceMap, indexPreCloseMap);
        String zone = ScoreZone.matchZone(score).getName();

        log.info("综合评分计算完成|Score_calculated,score={},zone={},date={}", score, zone, today);

        return score;
    }

    /**
     * 获取指数基准价
     * <p>
     * 优先使用内存缓存的当天第一条价格，如果没有则降级到 Redis 读取昨收价。
     *
     * @param windCode 指数代码
     * @return 基准价，如果不存在则返回 null
     */
    private Double getBasePrice(String windCode) {
        // 优先使用内存缓存的第一条价格
        Double firstPrice = firstPriceMap.get(windCode);
        if (firstPrice != null && firstPrice > 0) {
            return firstPrice;
        }

        // 降级到 Redis 读取昨收价
        return getPreClosePriceFromRedis(windCode);
    }

    /**
     * 从 Redis 读取指数昨收价（降级方案）
     *
     * @param windCode 指数代码
     * @return 昨收价，如果不存在则返回 null
     */
    private Double getPreClosePriceFromRedis(String windCode) {
        if (windCode == null || windCode.isEmpty()) {
            return null;
        }

        try {
            String key = RedisKeyConstants.RISK_INDEX_PRE_CLOSE_PREFIX + windCode;
            String value = redisTemplate.opsForValue().get(key);
            if (value != null && !value.isEmpty()) {
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            log.error("从Redis读取昨收价失败|Read_pre_close_from_redis_error,windCode={}", windCode, e);
        }

        return null;
    }

    /**
     * 获取当前缓存的指数价格（供调试使用）
     *
     * @return 指数价格 Map
     */
    public Map<String, Double> getCurrentPriceMap() {
        return new HashMap<>(currentPriceMap);
    }

    /**
     * 获取当前缓存的基准价（供调试使用）
     *
     * @return 基准价 Map
     */
    public Map<String, Double> getFirstPriceMap() {
        return new HashMap<>(firstPriceMap);
    }

    /**
     * 获取最后更新时间（供监控使用）
     *
     * @return 最后更新时间
     */
    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }
}
