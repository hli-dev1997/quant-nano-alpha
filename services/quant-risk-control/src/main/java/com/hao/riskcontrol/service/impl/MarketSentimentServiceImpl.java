package com.hao.riskcontrol.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.riskcontrol.common.enums.market.MarketSentimentScorer;
import com.hao.riskcontrol.common.enums.market.ScoreZone;
import com.hao.riskcontrol.dto.quotation.IndexQuotationDTO;
import dto.risk.MarketSentimentDTO;
import com.hao.riskcontrol.service.MarketSentimentService;
import constants.NumberFormatConstants;
import constants.RedisKeyConstants;
import enums.market.RiskMarketIndexEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
 *     <li>定时推送市场情绪分数到 Redis（供信号中心使用）</li>
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
    private final ObjectMapper objectMapper;

    /**
     * Redis Key：市场情绪分数
     * 与信号中心的 RiskControlClient 保持一致
     */
    private static final String REDIS_KEY_SENTIMENT_SCORE = RedisKeyConstants.MARKET_SENTIMENT_SCORE;

    /**
     * 分数缓存过期时间：5 分钟
     * 即使定时任务失败，最多 5 分钟后 Key 过期
     */
    private static final Duration SCORE_TTL = Duration.ofMinutes(5);

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
     * 价格更新标记
     * true：价格已更新，需要重新计算并推送
     * false：价格未更新，跳过本次定时任务
     */
    private volatile boolean priceUpdated = false;

    /**
     * 最后更新时间
     */
    private volatile LocalDateTime lastUpdateTime;

    @Override
    public void updateIndexPrice(IndexQuotationDTO dto) {
        // [TRACE-04] 价格更新开始
        log.info("[TRACE-04] 价格更新开始|Price_update_start,windCode={},latestPrice={},tradeDate={}",
                dto != null ? dto.getWindCode() : "null",
                dto != null ? dto.getLatestPrice() : "null",
                dto != null ? dto.getTradeDate() : "null");

        if (dto == null || dto.getWindCode() == null || dto.getLatestPrice() == null) {
            log.warn("[TRACE-WARN] 更新指数价格参数无效|Update_index_price_invalid,dto={}", dto);
            return;
        }

        LocalDate tradeDate = dto.getTradeDate() != null ? dto.getTradeDate().toLocalDate() : LocalDate.now();
        String windCode = dto.getWindCode();
        Double latestPrice = dto.getLatestPrice();

        // 判断是否跨交易日，如果是则清空缓存
        if (currentTradeDate == null || !currentTradeDate.equals(tradeDate)) {
            log.info("[TRACE-INFO] 检测到新交易日|New_trade_date_detected,oldDate={},newDate={}", currentTradeDate, tradeDate);
            firstPriceMap.clear();
            currentPriceMap.clear();
            currentTradeDate = tradeDate;
        }

        // 缓存当天第一条行情作为基准价（只存第一条）
        if (!firstPriceMap.containsKey(windCode)) {
            firstPriceMap.put(windCode, latestPrice);
            log.info("[TRACE-INFO] 第一条指数行情已缓存|First_index_price_cached,windCode={},price={},date={},totalCached={}",
                    windCode, latestPrice, tradeDate, firstPriceMap.size());
        }

        // 更新当前实时价格
        currentPriceMap.put(windCode, latestPrice);
        lastUpdateTime = dto.getTradeDate();
        priceUpdated = true;  // 标记价格已更新

        // [TRACE-05] 价格缓存更新完成
        Double basePrice = firstPriceMap.get(windCode);
        double changePercent = 0.0;
        if (basePrice != null && basePrice > 0) {
            changePercent = (latestPrice - basePrice) / basePrice * 100;
        }
        log.info("[TRACE-05] 价格缓存更新|Price_cached,windCode={},base={},current={},change={}%,currentMapSize={},firstMapSize={}",
                windCode,
                basePrice != null ? String.format(NumberFormatConstants.DECIMAL_2, basePrice) : "N/A",
                String.format(NumberFormatConstants.DECIMAL_2, latestPrice),
                String.format(NumberFormatConstants.DECIMAL_2, changePercent),
                currentPriceMap.size(),
                firstPriceMap.size());
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
     * 定时推送市场情绪分数到 Redis
     * <p>
     * 定时任务间隔：每 1 秒执行一次
     * <p>
     * 推送目的：
     * 供信号中心（quant-signal-center）实时读取，用于风控判断。
     * 使用 Redis 作为中间层实现跨服务的实时数据共享。
     * <p>
     * 容错设计：
     * 1. 无数据时不推送（避免覆盖有效数据）
     * 2. 设置 5 分钟过期时间（任务失败时自动降级）
     * 3. 推送失败只记录日志，不抛出异常
     */
    @Scheduled(fixedRate = 1000)
    public void pushScoreToRedis() {
        try {
            // 无数据时不推送
            if (currentPriceMap.isEmpty()) {
                log.debug("[TRACE-06] 定时任务跳过|Scheduled_skip,reason=no_price_data");
                return;
            }

            // 价格未更新时跳过推送
            if (!priceUpdated) {
                log.debug("[TRACE-06] 定时任务跳过|Scheduled_skip,reason=price_not_updated");
                return;
            }

            // [TRACE-06] 定时任务触发
            log.info("[TRACE-06] 定时任务触发|Scheduled_push_triggered,currentPriceCount={},firstPriceCount={}",
                    currentPriceMap.size(), firstPriceMap.size());

            long startTime = System.currentTimeMillis();
            int rawChange = calculateCurrentScore();
            long calcElapsed = System.currentTimeMillis() - startTime;

            // 转换为百分制分数并获取区间信息
            int percentageScore = MarketSentimentScorer.convertToPercentageScore(rawChange);
            ScoreZone zone = ScoreZone.matchZone(rawChange);

            // 构建 DTO（设置过期时间 = 当前时间 + 3秒）
            long now = System.currentTimeMillis();
            MarketSentimentDTO dto = MarketSentimentDTO.builder()
                    .score(percentageScore)
                    .rawChange(rawChange)
                    .zoneName(zone.getName())
                    .suggestion(zone.getOperationHint())
                    .timestamp(now)
                    .expireTimestamp(now + MarketSentimentDTO.DEFAULT_TTL_MS)
                    .formattedChange(String.format("%+.2f%%", rawChange / 100.0))
                    .build();

            // [FULL_CHAIN_STEP_11] 推送市场情绪分数到 Redis → 信号中心查询
            // @see docs/architecture/FullChainDataFlow.md
            String jsonValue = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(
                    REDIS_KEY_SENTIMENT_SCORE,
                    jsonValue,
                    SCORE_TTL
            );

            // 重置更新标记
            priceUpdated = false;

            // [TRACE-09] Redis 推送完成
            log.info("[TRACE-09] Redis推送完成|Redis_push_done,key={},score={},rawChange={},zone={},hint={},calcElapsedMs={}",
                    REDIS_KEY_SENTIMENT_SCORE, percentageScore, rawChange, zone.getName(), zone.getOperationHint(), calcElapsed);

        } catch (JsonProcessingException e) {
            log.error("[TRACE-ERROR] JSON序列化失败|Json_serialization_failed", e);
        } catch (Exception e) {
            log.error("[TRACE-ERROR] 市场情绪分数推送失败|Sentiment_score_push_failed", e);
            // 不抛出异常，避免定时任务中断
        }
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
            log.info("[TRACE-07] 当前无指数价格数据，返回0|No_index_price_data");
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
            log.warn("[TRACE-WARN] 无法获取基准价数据，返回0|No_base_price_data");
            return 0;
        }

        // [TRACE-07] 开始计算综合分数
        log.info("[TRACE-07] 开始计算综合分数|Score_calc_start,date={},indexCount={},basePriceCount={}",
                today, indexPriceMap.size(), indexPreCloseMap.size());

        // 调用核心计算方法
        int score = RiskMarketIndexEnum.calculateCompositeScore(today, indexPriceMap, indexPreCloseMap);
        ScoreZone zone = ScoreZone.matchZone(score);

        // [TRACE-08] 综合分数计算完成
        log.info("[TRACE-08] 综合分数计算完成|Score_calc_done,score={},zone={},hint={},date={}",
                score, zone.getName(), zone.getOperationHint(), today);

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
