package com.hao.signalcenter.service;

import constants.RedisKeyConstants;
import enums.strategy.StrategyRiskLevelEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 风控服务客户端 (Risk Control Client)
 * <p>
 * 类职责：
 * 从 Redis 读取市场情绪分数，支持降级处理。
 * <p>
 * 使用场景：
 * 信号中心消费 Kafka 信号时，旁路查询风控分数决定展示状态。
 * <p>
 * 降级策略：
 * 当 Redis 不可用或 Key 过期时：
 * 1. 返回中性分数 50
 * 2. 只有低风险策略可放行（标记为 NEUTRAL）
 * 3. 中高风险策略强制拦截（标记为 BLOCKED）
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class RiskControlClient {

    /**
     * 降级默认分数（中性）
     */
    public static final int FALLBACK_SCORE = 50;

    /**
     * 风控通过阈值
     * 分数 >= 60 认为市场情绪良好，允许展示
     */
    public static final int PASS_THRESHOLD = 60;

    /**
     * Redis Key：市场情绪分数
     * 由 quant-risk-control 模块每 10 秒更新
     */
    private static final String REDIS_KEY_SENTIMENT_SCORE = RedisKeyConstants.MARKET_SENTIMENT_SCORE;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 风控分数查询结果 (Risk Score Result)
     * <p>
     * 类职责：
     * 封装风控分数查询结果，包含分数值和是否处于降级模式的标志。
     * <p>
     * 设计目的：
     * 通过独立的 fallback 标志位判断降级状态，避免仅依赖分数值判断导致的误判问题。
     *
     * @author hli
     * @date 2026-01-30
     */
    public static class RiskScoreResult {

        /**
         * 风控分数（0-100）
         */
        private final int score;

        /**
         * 是否处于降级模式
         * true-Redis 不可用或 Key 不存在
         * false-正常从 Redis 读取到分数
         */
        private final boolean fallback;

        /**
         * 构造函数
         *
         * @param score    风控分数
         * @param fallback 是否降级模式
         */
        public RiskScoreResult(int score, boolean fallback) {
            this.score = score;
            this.fallback = fallback;
        }

        /**
         * 获取风控分数
         *
         * @return 风控分数（0-100）
         */
        public int getScore() {
            return score;
        }

        /**
         * 判断是否处于降级模式
         *
         * @return true-降级模式
         */
        public boolean isFallback() {
            return fallback;
        }

        @Override
        public String toString() {
            return "RiskScoreResult{" +
                    "score=" + score +
                    ", fallback=" + fallback +
                    '}';
        }
    }

    /**
     * 获取市场情绪分数及降级标志
     * <p>
     * 从 Redis 读取风控模块计算的市场情绪分数。
     * 若读取失败或 Key 不存在，返回降级分数并标记为降级模式。
     *
     * @return 包含分数和降级标志的结果对象
     */
    public RiskScoreResult getMarketSentimentScoreWithFallback() {
        try {
            String value = redisTemplate.opsForValue().get(REDIS_KEY_SENTIMENT_SCORE);
            if (value != null && !value.isBlank()) {
                int score = Integer.parseInt(value.trim());
                log.debug("风控分数读取成功|Risk_score_read,score={}", score);
                return new RiskScoreResult(score, false);  // 正常模式
            } else {
                log.warn("风控分数不存在_进入降级模式|Risk_score_missing_fallback");
                return new RiskScoreResult(FALLBACK_SCORE, true);  // 降级模式
            }
        } catch (Exception e) {
            log.error("风控服务熔断_进入降级模式|Risk_service_fallback,error={}", e.getMessage());
            return new RiskScoreResult(FALLBACK_SCORE, true);  // 降级模式
        }
    }

    /**
     * 获取市场情绪分数
     * <p>
     * 简化版本，仅返回分数。如需判断降级状态，请使用 getMarketSentimentScoreWithFallback()。
     *
     * @return 市场情绪分数（0-100），降级时返回 50
     */
    public int getMarketSentimentScore() {
        return getMarketSentimentScoreWithFallback().getScore();
    }

    /**
     * 判断风控是否通过
     * <p>
     * 非降级模式下，分数 >= 60 认为通过。
     *
     * @param score 市场情绪分数
     * @return true-风控通过
     */
    public boolean isRiskPassed(int score) {
        return score >= PASS_THRESHOLD;
    }

    /**
     * 判断降级模式下是否允许放行
     * <p>
     * 根据策略风险等级决定：
     * - LOW：允许放行（标记为 NEUTRAL）
     * - MEDIUM/HIGH：强制拦截
     *
     * @param riskLevelCode 策略风险等级编码
     * @return true-允许放行
     */
    public boolean isAllowedOnFallback(String riskLevelCode) {
        StrategyRiskLevelEnum riskLevel = StrategyRiskLevelEnum.fromCode(riskLevelCode);
        return riskLevel.isAllowedOnFallback();
    }
}

