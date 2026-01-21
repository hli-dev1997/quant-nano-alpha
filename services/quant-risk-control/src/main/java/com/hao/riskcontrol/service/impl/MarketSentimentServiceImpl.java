package com.hao.riskcontrol.service.impl;

import com.hao.riskcontrol.common.enums.market.MarketSentimentScorer;
import com.hao.riskcontrol.common.enums.market.ScoreZone;
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
 *     <li>从 Redis 读取各指数的昨日收盘价（由 Data-Collector 模块写入）</li>
 *     <li>调用 RiskMarketIndexEnum 计算综合涨跌幅</li>
 *     <li>调用 MarketSentimentScorer 评估风险区间</li>
 * </ol>
 * <p>
 * 注意：昨收价缓存由 Data-Collector 模块负责写入，本服务仅负责读取。
 * 使用公共 Redis Key: {@link RedisKeyConstants#RISK_INDEX_PRE_CLOSE_PREFIX}
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
     * 最后更新时间
     */
    private volatile LocalDateTime lastUpdateTime;

    @Override
    public void updateIndexPrice(String windCode, Double latestPrice, LocalDateTime tradeTime) {
        if (windCode == null || latestPrice == null) {
            log.warn("更新指数价格参数无效|Update_index_price_invalid,windCode={},price={}", windCode, latestPrice);
            return;
        }

        // 更新内存缓存
        currentPriceMap.put(windCode, latestPrice);
        lastUpdateTime = tradeTime;

        if (log.isDebugEnabled()) {
            log.debug("指数价格已更新|Index_price_updated,windCode={},price={},time={}",
                    windCode, latestPrice, tradeTime);
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
        lastUpdateTime = null;
        log.info("市场情绪服务已重置|Market_sentiment_service_reset");
    }

    /**
     * 计算当前综合评分
     * <p>
     * 1. 获取各指数昨收价（从 Redis 读取，由 Data-Collector 写入）
     * 2. 获取各指数当前价（从内存缓存）
     * 3. 调用 RiskMarketIndexEnum.calculateCompositeScore 计算
     *
     * @return 综合评分（整数基点）
     */
    private int calculateCurrentScore() {
        if (currentPriceMap.isEmpty()) {
            log.debug("当前无指数价格数据，返回0|No_index_price_data");
            return 0;
        }

        LocalDate today = LocalDate.now();

        // 构建当前价格 Map
        Map<String, Double> indexPriceMap = new HashMap<>(currentPriceMap);

        // 从 Redis 获取昨收价 Map
        Map<String, Double> indexPreCloseMap = new HashMap<>();
        for (String windCode : indexPriceMap.keySet()) {
            Double preClose = getPreClosePriceFromRedis(windCode);
            if (preClose != null && preClose > 0) {
                indexPreCloseMap.put(windCode, preClose);
            }
        }

        if (indexPreCloseMap.isEmpty()) {
            log.warn("无法获取昨收价数据，返回0|No_pre_close_data");
            return 0;
        }

        // 调用核心计算方法
        int score = RiskMarketIndexEnum.calculateCompositeScore(today, indexPriceMap, indexPreCloseMap);

        if (log.isDebugEnabled()) {
            log.debug("综合评分计算完成|Composite_score_calculated,score={},zone={}",
                    score, ScoreZone.matchZone(score).getName());
        }

        return score;
    }

    /**
     * 从 Redis 读取指数昨收价
     * <p>
     * 使用公共 Redis Key: {@link RedisKeyConstants#RISK_INDEX_PRE_CLOSE_PREFIX} + windCode
     * 昨收价由 Data-Collector 模块负责写入。
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
     * 获取最后更新时间（供监控使用）
     *
     * @return 最后更新时间
     */
    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }
}
