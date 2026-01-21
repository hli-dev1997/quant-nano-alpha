package com.hao.riskcontrol.common.enums.market;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 市场情绪百分制评分器 (Market Sentiment Percentage Scorer)
 *
 * <p>将综合市场情绪分数转换为直观的100分制得分，并定义对应的市场状态等级。
 * Converts the composite market sentiment score into an intuitive 100-point score
 * and defines corresponding market state levels.
 *
 * <h3>评分标准统一说明 (Unified Scoring Standard):</h3>
 * <p>全部使用整数百分制，无小数：
 * <ul>
 *   <li>输入：综合分数 150 = 150基点，-80 = -80基点</li>
 *   <li>输出：百分制得分 0-100 分</li>
 *   <li>与 {@link enums.market.RiskMarketIndexEnum#calculateCompositeScore} 返回值直接对接</li>
 * </ul>
 *
 * <h3>设计亮点 (Design Highlights):</h3>
 * <ul>
 *   <li>策略模式：评分逻辑委托给 {@link ScoreZone} 枚举，消除 if-else 链</li>
 *   <li>零魔法值：所有阈值和分数边界由枚举常量定义</li>
 *   <li>单一职责：本类仅负责协调调用，具体计算由各区间枚举实现</li>
 * </ul>
 *
 * @author haoli
 * @see ScoreZone
 */
@Slf4j
public class MarketSentimentScorer {

    // ==================== 私有构造器（工具类模式） ====================

    private MarketSentimentScorer() {
        // 工具类禁止实例化 (Utility class - prevent instantiation)
    }

    // ==================== 核心评分方法 ====================

    /**
     * 将综合市场情绪分数转换为百分制得分
     * Convert composite market sentiment score to percentage score
     *
     * <p>采用策略模式，自动匹配对应区间并计算得分，替代 if-else 链。
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点，-80 = -80基点）
     * @return 百分制得分 (0 - 100)
     */
    public static int convertToPercentageScore(int compositeScore) {
        ScoreZone zone = ScoreZone.matchZone(compositeScore);
        int score = zone.calculateScore(compositeScore);

        log.debug("score_calculation | 评分计算完成 | compositeScore={}, zone={}, percentageScore={}",
                compositeScore, zone.getName(), score);

        return score;
    }

    /**
     * 根据百分制得分判断市场状态区间
     * Assess market zone by percentage score
     *
     * @param percentageScore 百分制得分 (0 - 100)
     * @return 市场状态区间枚举
     */
    public static ScoreZone assessMarketZone(int percentageScore) {
        return ScoreZone.fromPercentageScore(percentageScore);
    }

    /**
     * 根据百分制得分获取市场状态名称（中文）
     * Get market state name by percentage score
     *
     * @param percentageScore 百分制得分 (0 - 100)
     * @return 市场状态名称
     */
    public static String assessMarketByScore(int percentageScore) {
        return assessMarketZone(percentageScore).getName();
    }

    /**
     * 一站式评估：从综合分数直接获取市场区间
     * One-stop assessment: get market zone directly from composite score
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点）
     * @return 市场区间枚举
     */
    public static ScoreZone evaluate(int compositeScore) {
        return ScoreZone.matchZone(compositeScore);
    }

    /**
     * 完整评估结果，包含得分和状态
     * Complete evaluation result with score and state
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点）
     * @return 评估结果对象
     */
    public static EvaluationResult evaluateWithDetails(int compositeScore) {
        int percentageScore = convertToPercentageScore(compositeScore);
        ScoreZone zone = assessMarketZone(percentageScore);
        return new EvaluationResult(compositeScore, percentageScore, zone);
    }

    // ==================== 评估结果DTO ====================

    /**
     * 市场情绪评估结果 (Market Sentiment Evaluation Result)
     *
     * <p>采用不可变对象设计，线程安全。
     */
    @Getter
    public static class EvaluationResult {

        /**
         * 原始综合分数（整数百分制，150 = 150基点）
         */
        private final int compositeScore;

        /**
         * 百分制得分（0-100）
         */
        private final int percentageScore;

        /**
         * 市场区间
         */
        private final ScoreZone zone;

        public EvaluationResult(int compositeScore, int percentageScore, ScoreZone zone) {
            this.compositeScore = compositeScore;
            this.percentageScore = percentageScore;
            this.zone = zone;
        }

        /**
         * 获取格式化的综合分数（百分比形式）
         *
         * @return 例如 "+150bp" 或 "-80bp"
         */
        public String getFormattedCompositeScore() {
            return String.format("%+dbp", compositeScore);
        }

        /**
         * 获取市场状态名称
         */
        public String getMarketStateName() {
            return zone.getName();
        }

        /**
         * 获取操作建议
         */
        public String getOperationHint() {
            return zone.getOperationHint();
        }

        /**
         * 获取完整的评估摘要
         *
         * @return 格式化的评估描述
         */
        public String getSummary() {
            return String.format("综合涨跌幅: %s | 百分制得分: %d分 | 市场状态: %s | 建议: %s",
                    getFormattedCompositeScore(),
                    percentageScore,
                    zone.getName(),
                    zone.getOperationHint());
        }

        @Override
        public String toString() {
            return getSummary();
        }
    }
}
