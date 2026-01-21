package com.hao.riskcontrol.common.enums.market;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 市场评分区间枚举 (Market Score Zone Enum)
 *
 * <p>定义市场情绪评分的各个区间及其评分规则，消除魔法值，支持策略模式计算。
 * Defines market sentiment scoring zones and their calculation rules,
 * eliminating magic values and supporting strategy pattern for scoring.
 *
 * <h3>评分标准统一说明 (Unified Scoring Standard):</h3>
 * <p>全部使用整数百分制，无小数：
 * <ul>
 *   <li>输入：综合分数 150 = 涨跌150基点，-80 = 涨跌-80基点</li>
 *   <li>输出：百分制得分 0-100 分</li>
 *   <li>区间边界：50 = 50基点，200 = 200基点</li>
 * </ul>
 *
 * <h3>设计理念 (Design Philosophy):</h3>
 * <ul>
 *   <li>枚举承载行为：每个区间自带评分计算逻辑，替代 if-else 链</li>
 *   <li>常量集中管理：所有阈值和分数定义在枚举常量中，无魔法值</li>
 *   <li>开闭原则：新增区间只需添加枚举值，无需修改计算逻辑</li>
 * </ul>
 *
 * @author haoli
 */
@Getter
@Slf4j
public enum ScoreZone {

    /**
     * 强势上涨区：综合分数 ≥ 50，得分75-100
     * Strong bullish zone: composite score ≥ 50 (i.e. +50bp), score 75-100
     */
    STRONG_BULLISH(
            50, Integer.MAX_VALUE,
            75, 100,
            150,
            "强势区", "Strong Bullish Zone", "积极进攻"
    ) {
        @Override
        public int calculateScore(int compositeScore) {
            double rawScore = getBaseScore() + (compositeScore - getLowerBound()) * ((double) getScoreRange() / getZoneRange());
            return (int) Math.min(getMaxScore(), rawScore);
        }
    },

    /**
     * 震荡偏强区：0 ≤ 综合分数 < 50，得分50-74
     * Mildly bullish zone: 0 ≤ composite score < 50, score 50-74
     */
    MILDLY_BULLISH(
            0, 50,
            50, 74,
            50,
            "震荡偏强区", "Mildly Bullish Zone", "持仓或优化"
    ) {
        @Override
        public int calculateScore(int compositeScore) {
            return (int) (getBaseScore() + compositeScore * ((double) getScoreRange() / getZoneRange()));
        }
    },

    /**
     * 震荡偏弱区：-50 ≤ 综合分数 < 0，得分25-49
     * Mildly bearish zone: -50 ≤ composite score < 0, score 25-49
     */
    MILDLY_BEARISH(
            -50, 0,
            25, 49,
            50,
            "震荡偏弱区", "Mildly Bearish Zone", "防御减仓"
    ) {
        @Override
        public int calculateScore(int compositeScore) {
            return (int) (getBaseScore() + (compositeScore - getLowerBound()) * ((double) getScoreRange() / getZoneRange()));
        }
    },

    /**
     * 弱势下跌区：综合分数 < -50，得分0-24
     * Strong bearish zone: composite score < -50 (i.e. -50bp), score 0-24
     */
    STRONG_BEARISH(
            Integer.MIN_VALUE, -50,
            0, 24,
            150,
            "弱势区", "Strong Bearish Zone", "严格风控"
    ) {
        @Override
        public int calculateScore(int compositeScore) {
            // 从25分基准向下计算：-50→25分, -200→0分
            double rawScore = getMaxScore() + 1 + (compositeScore - getUpperBound()) * ((double) getScoreRange() / getZoneRange());
            return (int) Math.max(getBaseScore(), rawScore);
        }
    };

    // ==================== 区间边界参数 ====================

    /**
     * 区间下界（整数百分制，50 = 50基点）
     */
    private final int lowerBound;

    /**
     * 区间上界（整数百分制）
     */
    private final int upperBound;

    /**
     * 基础分数（区间起点得分）
     */
    private final int baseScore;

    /**
     * 最高分数（区间终点得分）
     */
    private final int maxScore;

    /**
     * 区间跨度（整数百分制，150 = 150基点跨度）
     */
    private final int zoneRange;

    /**
     * 中文名称
     */
    private final String name;

    /**
     * 英文名称
     */
    private final String englishName;

    /**
     * 操作建议
     */
    private final String operationHint;

    ScoreZone(int lowerBound, int upperBound,
              int baseScore, int maxScore, int zoneRange,
              String name, String englishName, String operationHint) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.baseScore = baseScore;
        this.maxScore = maxScore;
        this.zoneRange = zoneRange;
        this.name = name;
        this.englishName = englishName;
        this.operationHint = operationHint;
    }

    // ==================== 核心评分方法 ====================

    /**
     * 计算该区间内的百分制得分（模板方法，子类实现）
     * Calculate percentage score within this zone (template method)
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点）
     * @return 百分制得分 (0-100)
     */
    public abstract int calculateScore(int compositeScore);

    /**
     * 判断给定分数是否属于当前区间
     * Check if the given score falls within this zone
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点）
     * @return true if score is in this zone
     */
    public boolean contains(int compositeScore) {
        return compositeScore >= lowerBound && compositeScore < upperBound;
    }

    /**
     * 获取分数跨度（最高分 - 基础分）
     * Get score range (max score - base score)
     */
    public int getScoreRange() {
        return maxScore - baseScore + 1;
    }

    // ==================== 静态查找方法 ====================

    /**
     * 根据综合分数自动匹配对应区间（替代 if-else 链）
     * Match score zone by composite score (replaces if-else chain)
     *
     * @param compositeScore 综合分数（整数百分制，150 = 150基点）
     * @return 匹配的评分区间
     */
    public static ScoreZone matchZone(int compositeScore) {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(ScoreZone::getLowerBound).reversed())
                .filter(zone -> zone.contains(compositeScore))
                .findFirst()
                .orElse(STRONG_BEARISH);
    }

    /**
     * 根据百分制得分查找对应区间
     * Find zone by percentage score
     *
     * @param percentageScore 百分制得分 (0-100)
     * @return 匹配的评分区间
     */
    public static ScoreZone fromPercentageScore(int percentageScore) {
        return Arrays.stream(values())
                .filter(zone -> percentageScore >= zone.getBaseScore() && percentageScore <= zone.getMaxScore())
                .findFirst()
                .orElse(STRONG_BEARISH);
    }
}
