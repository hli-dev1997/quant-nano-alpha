package com.hao.riskcontrol.service;

import com.hao.riskcontrol.common.enums.market.MarketSentimentScorer;

import java.time.LocalDateTime;

/**
 * 市场情绪评分服务接口
 * <p>
 * 负责接收指数实时行情数据，计算综合市场情绪评分。
 * 评分逻辑基于 {@link com.hao.riskcontrol.common.enums.market.RiskMarketIndexEnum} 和
 * {@link MarketSentimentScorer}。
 *
 * @author hli
 * @date 2026-01-18
 */
public interface MarketSentimentService {

    /**
     * 更新单个指数的实时价格
     * <p>
     * 由 Kafka 消费者调用，每次收到指数行情数据时更新。
     *
     * @param windCode    指数代码（如 000300.SH）
     * @param latestPrice 最新价格
     * @param tradeTime   交易时间
     */
    void updateIndexPrice(String windCode, Double latestPrice, LocalDateTime tradeTime);

    /**
     * 获取当前综合市场情绪评估结果
     * <p>
     * 基于已接收的各指数实时价格和昨日收盘价计算综合评分。
     *
     * @return 评估结果（包含综合分数、区间、策略建议等）
     */
    MarketSentimentScorer.EvaluationResult getCurrentEvaluation();

    /**
     * 获取当前综合评分（整数基点）
     * <p>
     * 便捷方法，直接返回综合分数值。
     *
     * @return 综合评分（整数基点，如 150 = 1.5%）
     */
    int getCurrentCompositeScore();

    /**
     * 重置状态
     * <p>
     * 清空当前缓存的价格数据，用于新交易日开始或测试。
     */
    void reset();
}
