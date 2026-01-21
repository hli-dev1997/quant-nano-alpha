package com.hao.riskcontrol.common.enums.market;

import enums.market.RiskMarketIndexEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 市场情绪评分系统全面测试类 (Market Sentiment Scoring System Comprehensive Test)
 *
 * <p>测试目的：
 * <ol>
 *   <li>验证 {@link RiskMarketIndexEnum} 权重计算和综合分数计算逻辑</li>
 *   <li>验证 {@link ScoreZone} 区间匹配和评分计算逻辑</li>
 *   <li>验证 {@link MarketSentimentScorer} 端到端评估流程</li>
 *   <li>验证边界条件和异常场景处理</li>
 *   <li>验证整数百分制统一标准的正确性</li>
 * </ol>
 *
 * <p>设计思路：
 * <ul>
 *   <li>使用嵌套测试类分层组织测试用例，结构清晰</li>
 *   <li>使用参数化测试覆盖多种输入场景，减少重复代码</li>
 *   <li>覆盖正常场景、边界场景、异常场景，确保鲁棒性</li>
 *   <li>验证评分公式的数学正确性</li>
 * </ul>
 *
 * @author haoli
 * @see RiskMarketIndexEnum
 * @see ScoreZone
 * @see MarketSentimentScorer
 */
@Slf4j
@DisplayName("市场情绪评分系统全面测试")
class MarketSentimentScoringSystemTest {

    // ==================== RiskMarketIndexEnum 测试 ====================

    @Nested
    @DisplayName("RiskMarketIndexEnum 指数枚举测试")
    class RiskMarketIndexEnumTest {

        @Test
        @DisplayName("验证核心加权指数权重总和为100")
        void testCoreIndicesWeightSumEquals100() {
            // 实现思路：
            // 1. 获取所有权重大于0的核心指数
            // 2. 累加权重验证总和为100（百分制）

            int totalWeight = 0;
            for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
                if (index.getDefaultWeight() > 0) {
                    totalWeight += index.getDefaultWeight();
                    log.debug("指数权重|index_weight,name={},weight={}", index.getName(), index.getDefaultWeight());
                }
            }

            log.info("权重总和验证|weight_sum_verification,totalWeight={}", totalWeight);
            assertEquals(100, totalWeight, "核心指数权重总和应为100");
        }

        @Test
        @DisplayName("验证2020年前的日期应排除科创50指数")
        void testStar50ExcludedBefore2020() {
            // 实现思路：
            // 1. 选取2020-07-23之前的日期
            // 2. 获取生效指数列表
            // 3. 验证科创50不在列表中

            LocalDate dateBefore = LocalDate.of(2020, 7, 22);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dateBefore);

            boolean hasStar50 = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.STAR_50);

            log.info("科创50排除验证|star50_exclusion_check,date={},hasStar50={}", dateBefore, hasStar50);
            assertFalse(hasStar50, "2020-07-23之前科创50指数应被排除");
        }

        @Test
        @DisplayName("验证2020年后的日期应包含科创50指数")
        void testStar50IncludedAfter2020() {
            LocalDate dateAfter = LocalDate.of(2020, 7, 23);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dateAfter);

            boolean hasStar50 = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.STAR_50);

            log.info("科创50包含验证|star50_inclusion_check,date={},hasStar50={}", dateAfter, hasStar50);
            assertTrue(hasStar50, "2020-07-23及之后科创50指数应被包含");
        }

        @Test
        @DisplayName("验证2020年前有效指数权重总和为95（不含科创50）")
        void testWeightSumBefore2020() {
            // 科创50权重为5，排除后总权重应为100-5=95
            LocalDate dateBefore = LocalDate.of(2020, 1, 2);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dateBefore);

            int totalWeight = indices.stream()
                    .mapToInt(RiskMarketIndexEnum::getDefaultWeight)
                    .sum();

            log.info("历史权重验证|historical_weight_check,date={},totalWeight={},indexCount={}",
                    dateBefore, totalWeight, indices.size());
            assertEquals(95, totalWeight, "2020-07-23之前有效指数权重总和应为95");
            assertEquals(5, indices.size(), "2020-07-23之前应有5个有效加权指数");
        }

        @Test
        @DisplayName("验证科创50生效日期边界 - 前一天不包含")
        void testStar50EffectiveDateBoundaryBefore() {
            LocalDate dayBefore = LocalDate.of(2020, 7, 22);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dayBefore);

            boolean hasStar50 = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.STAR_50);

            assertFalse(hasStar50, "2020-07-22科创50应被排除");
            assertEquals(5, indices.size(), "2020-07-22应有5个有效加权指数");
        }

        @Test
        @DisplayName("验证科创50生效日期边界 - 当天包含")
        void testStar50EffectiveDateBoundaryExact() {
            LocalDate exactDate = LocalDate.of(2020, 7, 23);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(exactDate);

            boolean hasStar50 = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.STAR_50);

            assertTrue(hasStar50, "2020-07-23科创50应被包含");
            assertEquals(6, indices.size(), "2020-07-23应有6个有效加权指数");
        }

        @Test
        @DisplayName("验证历史日期综合分数计算 - 2020年前权重归一化")
        void testCompositeScoreWithHistoricalDateBeforeStar50() {
            // 2020年1月2日：无科创50，有效权重为95
            // 所有指数上涨1%，归一化后综合分数应仍为100
            LocalDate historicalDate = LocalDate.of(2020, 1, 2);
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            // 设置5个有效指数（不含科创50）全部上涨1%
            List<RiskMarketIndexEnum> validIndices = RiskMarketIndexEnum.getWeightedIndicesForDate(historicalDate);
            for (RiskMarketIndexEnum index : validIndices) {
                preCloseMap.put(index.getCode(), 1000.0);
                priceMap.put(index.getCode(), 1010.0); // 上涨1%
            }

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(historicalDate, priceMap, preCloseMap);

            log.info("历史日期综合分数验证|historical_score_check,date={},validCount={},compositeScore={}",
                    historicalDate, validIndices.size(), compositeScore);
            // 归一化后：(1% * 95) / 95 = 1% = 100基点
            assertEquals(100, compositeScore, "历史日期全部上涨1%时综合分数应为100（权重归一化）");
        }

        @Test
        @DisplayName("验证2014年前有效指数（不含中证1000）")
        void testIndicesBeforeCSI1000() {
            // 中证1000生效日期为2014-10-17
            LocalDate dateBefore = LocalDate.of(2014, 10, 16);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dateBefore);

            boolean hasCSI1000 = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.CSI_1000);

            // 2014-10-16: 国证A指(25) + 沪深300(25) + 中证500(20) + 创业板指(10) = 80
            int totalWeight = indices.stream()
                    .mapToInt(RiskMarketIndexEnum::getDefaultWeight)
                    .sum();

            assertFalse(hasCSI1000, "2014-10-16中证1000应被排除");
            assertEquals(80, totalWeight, "2014-10-16有效指数权重总和应为80");
            log.info("中证1000排除验证|csi1000_exclusion_check,date={},totalWeight={}", dateBefore, totalWeight);
        }

        @Test
        @DisplayName("验证2010年前有效指数（不含国证A指和创业板指）")
        void testIndicesBeforeGZAIndex() {
            // 国证A指生效日期为2010-01-01，创业板指生效日期为2010-06-01
            LocalDate dateBefore = LocalDate.of(2009, 12, 31);
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(dateBefore);

            boolean hasGZAIndex = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.GZ_A_INDEX);
            boolean hasCHINEXT = indices.stream()
                    .anyMatch(i -> i == RiskMarketIndexEnum.CHINEXT);

            // 2009-12-31: 沪深300(25) + 中证500(20) = 45
            int totalWeight = indices.stream()
                    .mapToInt(RiskMarketIndexEnum::getDefaultWeight)
                    .sum();

            assertFalse(hasGZAIndex, "2009-12-31国证A指应被排除");
            assertFalse(hasCHINEXT, "2009-12-31创业板指应被排除");
            assertEquals(45, totalWeight, "2009-12-31有效指数权重总和应为45");
            log.info("早期指数排除验证|early_indices_exclusion_check,date={},totalWeight={}", dateBefore, totalWeight);
        }

        @Test
        @DisplayName("验证早期日期综合分数计算 - 权重自动归一化")
        void testCompositeScoreWithEarlyDate() {
            // 2009年：只有沪深300(25)和中证500(20)有效
            LocalDate earlyDate = LocalDate.of(2009, 1, 5);
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            // 沪深300上涨2%
            preCloseMap.put("000300.SH", 1000.0);
            priceMap.put("000300.SH", 1020.0);

            // 中证500上涨1%
            preCloseMap.put("000905.SH", 1000.0);
            priceMap.put("000905.SH", 1010.0);

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(earlyDate, priceMap, preCloseMap);

            // 归一化计算: (2%*25 + 1%*20) / 45 = (50 + 20) / 45 = 70/45 = 1.555%
            // 转换为基点: 1.555% * 100 = 156基点（四舍五入）
            log.info("早期日期综合分数验证|early_date_score_check,date={},compositeScore={}", earlyDate, compositeScore);
            assertEquals(156, compositeScore, "早期日期权重归一化后综合分数应为156基点");
        }

        @Test
        @DisplayName("验证所有指数的生效日期格式正确")
        void testAllIndicesEffectiveDateFormat() {
            for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
                String effectiveDate = index.getEffectiveDate();
                assertNotNull(effectiveDate, index.getName() + "生效日期不应为空");
                assertFalse(effectiveDate.isEmpty(), index.getName() + "生效日期不应为空字符串");

                // 验证日期格式可以正确解析
                assertDoesNotThrow(() -> LocalDate.parse(effectiveDate),
                        index.getName() + "生效日期格式应为yyyy-MM-dd");

                log.debug("生效日期验证|effective_date_check,index={},effectiveDate={}",
                        index.getName(), effectiveDate);
            }
            log.info("所有指数生效日期格式验证通过|all_effective_dates_valid");
        }

        @Test
        @DisplayName("验证当前日期所有核心指数均有效")
        void testAllCoreIndicesValidForToday() {
            LocalDate today = LocalDate.now();
            List<RiskMarketIndexEnum> indices = RiskMarketIndexEnum.getWeightedIndicesForDate(today);

            assertEquals(6, indices.size(), "当前日期应有6个有效加权指数");

            int totalWeight = indices.stream()
                    .mapToInt(RiskMarketIndexEnum::getDefaultWeight)
                    .sum();
            assertEquals(100, totalWeight, "当前日期有效指数权重总和应为100");

            log.info("当前日期指数验证|today_indices_check,date={},count={},totalWeight={}",
                    today, indices.size(), totalWeight);
        }

        @Test
        @DisplayName("验证综合分数计算 - 全部上涨1%应返回100")
        void testCompositeScoreAllUp1Percent() {
            // 实现思路：
            // 1. 构造所有指数上涨1%的行情数据
            // 2. 计算综合分数
            // 3. 验证结果为100（整数百分制，1% = 100）

            LocalDate today = LocalDate.now();
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            // 设置所有核心指数上涨1%
            for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
                if (index.getDefaultWeight() > 0) {
                    preCloseMap.put(index.getCode(), 1000.0);
                    priceMap.put(index.getCode(), 1010.0); // 上涨1%
                }
            }

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(today, priceMap, preCloseMap);

            log.info("综合分数计算验证|composite_score_check,expected=100,actual={}", compositeScore);
            assertEquals(100, compositeScore, "全部上涨1%时综合分数应为100");
        }

        @Test
        @DisplayName("验证综合分数计算 - 全部下跌150基点应返回-150")
        void testCompositeScoreAllDown1Point5Percent() {
            LocalDate today = LocalDate.now();
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            for (RiskMarketIndexEnum index : RiskMarketIndexEnum.values()) {
                if (index.getDefaultWeight() > 0) {
                    preCloseMap.put(index.getCode(), 1000.0);
                    priceMap.put(index.getCode(), 985.0); // 下跌150基点
                }
            }

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(today, priceMap, preCloseMap);

            log.info("综合分数计算验证|composite_score_check,expected=-150,actual={}", compositeScore);
            assertEquals(-150, compositeScore, "全部下跌150基点时综合分数应为-150");
        }

        @Test
        @DisplayName("验证综合分数计算 - 空数据应返回0")
        void testCompositeScoreWithEmptyData() {
            LocalDate today = LocalDate.now();
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(today, priceMap, preCloseMap);

            log.info("空数据综合分数验证|empty_data_check,compositeScore={}", compositeScore);
            assertEquals(0, compositeScore, "空数据时综合分数应为0");
        }

        @Test
        @DisplayName("验证综合分数计算 - 部分数据缺失时权重归一化")
        void testCompositeScoreWithPartialData() {
            // 实现思路：
            // 1. 只提供沪深300和中证500的数据（权重25+20=45）
            // 2. 沪深300上涨2%，中证500上涨1%
            // 3. 归一化后返回正数综合分数

            LocalDate today = LocalDate.now();
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            // 沪深300上涨2%
            preCloseMap.put("000300.SH", 1000.0);
            priceMap.put("000300.SH", 1020.0);

            // 中证500上涨1%
            preCloseMap.put("000905.SH", 1000.0);
            priceMap.put("000905.SH", 1010.0);

            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(today, priceMap, preCloseMap);

            // 加权计算: (0.02*25 + 0.01*20) / 45 * 10000 = 0.7/45*10000 ≈ 156
            // 但由于只有部分数据，归一化后实际为: ((200*25 + 100*20)/45) ≈ 156
            // 注意：数据缺失的指数不参与计算，权重按实际参与的指数归一化
            log.info("部分数据归一化验证|partial_data_check,compositeScore={}", compositeScore);
            
            // 验证结果在合理范围内（部分指数上涨应有正数综合分数）
            assertTrue(compositeScore > 0, 
                    "部分数据缺失时综合分数应为正, actual=" + compositeScore);
        }
    }

    // ==================== ScoreZone 测试 ====================

    @Nested
    @DisplayName("ScoreZone 评分区间测试")
    class ScoreZoneTest {

        @ParameterizedTest(name = "综合分数={0}应匹配{1}区间")
        @CsvSource({
                "200, STRONG_BULLISH",      // 200(涨2%) -> 强势区
                "100, STRONG_BULLISH",      // 100(涨1%) -> 强势区
                "50, STRONG_BULLISH",       // 50边界 -> 强势区
                "49, MILDLY_BULLISH",       // 49 -> 震荡偏强区
                "25, MILDLY_BULLISH",       // 25 -> 震荡偏强区
                "0, MILDLY_BULLISH",        // 0边界 -> 震荡偏强区
                "-1, MILDLY_BEARISH",       // -1 -> 震荡偏弱区
                "-25, MILDLY_BEARISH",      // -25 -> 震荡偏弱区
                "-50, MILDLY_BEARISH",      // -50边界 -> 震荡偏弱区
                "-51, STRONG_BEARISH",      // -51 -> 弱势区
                "-100, STRONG_BEARISH",     // -100(跌1%) -> 弱势区
                "-200, STRONG_BEARISH"      // -200(跌2%) -> 弱势区
        })
        @DisplayName("验证综合分数到区间的匹配")
        void testZoneMatching(int compositeScore, String expectedZoneName) {
            ScoreZone zone = ScoreZone.matchZone(compositeScore);

            log.debug("区间匹配测试|zone_matching,compositeScore={},expectedZone={},actualZone={}",
                    compositeScore, expectedZoneName, zone.name());
            assertEquals(expectedZoneName, zone.name(), 
                    "综合分数" + compositeScore + "应匹配" + expectedZoneName);
        }

        @ParameterizedTest(name = "综合分数={0}应得分{1}")
        @CsvSource({
                "200, 100",     // 200(涨2%) -> 满分100
                "125, 88",      // 125 -> 88分
                "50, 75",       // 50边界 -> 75分基准
                "25, 62",       // 25 -> 62分
                "0, 50",        // 0平衡点 -> 50分中性
                "-25, 37",      // -25 -> 37分
                "-50, 25",      // -50边界 -> 25分基准
                "-125, 12",     // -125 -> 12分
                "-200, 0"       // -200(跌2%) -> 最低0分
        })
        @DisplayName("验证综合分数到百分制得分的计算")
        void testScoreCalculation(int compositeScore, int expectedScore) {
            ScoreZone zone = ScoreZone.matchZone(compositeScore);
            int actualScore = zone.calculateScore(compositeScore);

            log.debug("评分计算测试|score_calculation,compositeScore={},zone={},expectedScore={},actualScore={}",
                    compositeScore, zone.getName(), expectedScore, actualScore);
            assertEquals(expectedScore, actualScore, 
                    "综合分数" + compositeScore + "应得" + expectedScore + "分");
        }

        @Test
        @DisplayName("验证极端大涨场景 - 得分不超过100")
        void testExtremeBullishCapped() {
            int compositeScore = 500; // 5%大涨
            ScoreZone zone = ScoreZone.matchZone(compositeScore);
            int score = zone.calculateScore(compositeScore);

            log.info("极端大涨封顶验证|extreme_bullish_cap,compositeScore={},score={}", compositeScore, score);
            assertTrue(score <= 100, "即使综合分数极高，得分也不应超过100");
            assertEquals(100, score, "5%大涨应得满分100");
        }

        @Test
        @DisplayName("验证极端大跌场景 - 得分不低于0")
        void testExtremeBearishFloored() {
            int compositeScore = -500; // -5%大跌
            ScoreZone zone = ScoreZone.matchZone(compositeScore);
            int score = zone.calculateScore(compositeScore);

            log.info("极端大跌保底验证|extreme_bearish_floor,compositeScore={},score={}", compositeScore, score);
            assertTrue(score >= 0, "即使综合分数极低，得分也不应低于0");
            assertEquals(0, score, "-5%大跌应得0分");
        }

        @Test
        @DisplayName("验证根据百分制得分查找区间")
        void testFromPercentageScore() {
            assertEquals(ScoreZone.STRONG_BULLISH, ScoreZone.fromPercentageScore(100));
            assertEquals(ScoreZone.STRONG_BULLISH, ScoreZone.fromPercentageScore(75));
            assertEquals(ScoreZone.MILDLY_BULLISH, ScoreZone.fromPercentageScore(74));
            assertEquals(ScoreZone.MILDLY_BULLISH, ScoreZone.fromPercentageScore(50));
            assertEquals(ScoreZone.MILDLY_BEARISH, ScoreZone.fromPercentageScore(49));
            assertEquals(ScoreZone.MILDLY_BEARISH, ScoreZone.fromPercentageScore(25));
            assertEquals(ScoreZone.STRONG_BEARISH, ScoreZone.fromPercentageScore(24));
            assertEquals(ScoreZone.STRONG_BEARISH, ScoreZone.fromPercentageScore(0));

            log.info("百分制得分查找区间验证通过|percentage_score_lookup_passed");
        }
    }

    // ==================== MarketSentimentScorer 测试 ====================

    @Nested
    @DisplayName("MarketSentimentScorer 评分器测试")
    class MarketSentimentScorerTest {

        @ParameterizedTest(name = "综合分数{0}应评为{1}分")
        @CsvSource({
                "200, 100",     // 200(涨2%) -> 满分
                "100, 83",      // 100(涨1%) -> 强势区
                "50, 75",       // 50边界 -> 强势区起点
                "0, 50",        // 0平衡点 -> 中性50分
                "-50, 25",      // -50边界 -> 弱势起点
                "-200, 0"       // -200(跌2%) -> 最低分
        })
        @DisplayName("验证综合分数转换为百分制得分")
        void testConvertToPercentageScore(int compositeScore, int expectedScore) {
            int actualScore = MarketSentimentScorer.convertToPercentageScore(compositeScore);

            log.debug("评分转换测试|score_conversion,compositeScore={},expectedScore={},actualScore={}",
                    compositeScore, expectedScore, actualScore);
            assertEquals(expectedScore, actualScore);
        }

        @Test
        @DisplayName("验证一站式评估功能")
        void testEvaluate() {
            // 强势区
            assertEquals(ScoreZone.STRONG_BULLISH, MarketSentimentScorer.evaluate(150));
            // 震荡偏强区
            assertEquals(ScoreZone.MILDLY_BULLISH, MarketSentimentScorer.evaluate(25));
            // 震荡偏弱区
            assertEquals(ScoreZone.MILDLY_BEARISH, MarketSentimentScorer.evaluate(-25));
            // 弱势区
            assertEquals(ScoreZone.STRONG_BEARISH, MarketSentimentScorer.evaluate(-100));

            log.info("一站式评估验证通过|one_stop_evaluation_passed");
        }

        @Test
        @DisplayName("验证完整评估结果对象")
        void testEvaluateWithDetails() {
            // 测试上涨150基点场景
            MarketSentimentScorer.EvaluationResult result = 
                    MarketSentimentScorer.evaluateWithDetails(150);

            assertEquals(150, result.getCompositeScore());
            assertTrue(result.getPercentageScore() >= 75);
            assertEquals(ScoreZone.STRONG_BULLISH, result.getZone());
            assertEquals("强势区", result.getMarketStateName());
            assertEquals("积极进攻", result.getOperationHint());
            assertTrue(result.getFormattedCompositeScore().contains("+150bp"));

            log.info("完整评估结果验证|full_evaluation_check,summary={}", result.getSummary());
        }

        @Test
        @DisplayName("验证评估结果摘要格式")
        void testEvaluationResultSummary() {
            MarketSentimentScorer.EvaluationResult result = 
                    MarketSentimentScorer.evaluateWithDetails(-80);

            String summary = result.getSummary();

            // 验证摘要包含所有必要信息
            assertTrue(summary.contains("-80bp"), "摘要应包含格式化的综合分数");
            assertTrue(summary.contains("分"), "摘要应包含百分制得分");
            assertTrue(summary.contains("震荡偏弱区") || summary.contains("弱势区"), "摘要应包含市场状态");
            assertTrue(summary.contains("建议"), "摘要应包含操作建议");

            log.info("评估结果摘要验证|summary_format_check,summary={}", summary);
        }

        @ParameterizedTest(name = "得分{0}应判定为{1}")
        @ValueSource(ints = {0, 24, 25, 49, 50, 74, 75, 100})
        @DisplayName("验证根据得分判断市场状态名称")
        void testAssessMarketByScore(int score) {
            String stateName = MarketSentimentScorer.assessMarketByScore(score);

            assertNotNull(stateName, "市场状态名称不应为空");
            assertFalse(stateName.isEmpty(), "市场状态名称不应为空字符串");

            log.debug("市场状态判断测试|market_state_assess,score={},stateName={}", score, stateName);
        }
    }

    // ==================== 端到端集成测试 ====================

    @Nested
    @DisplayName("端到端集成测试")
    class EndToEndIntegrationTest {

        @Test
        @DisplayName("验证完整评估流程 - 从指数行情到市场状态")
        void testFullEvaluationFlow() {
            // 实现思路：
            // 1. 模拟实际行情数据
            // 2. 计算综合分数
            // 3. 转换为百分制得分
            // 4. 获取市场状态
            // 5. 验证全流程结果一致性

            LocalDate today = LocalDate.now();
            Map<String, Double> priceMap = new HashMap<>();
            Map<String, Double> preCloseMap = new HashMap<>();

            // 模拟场景：沪深300涨100基点, 中证50050基点, 其他平盘
            preCloseMap.put("000300.SH", 4000.0);
            priceMap.put("000300.SH", 4040.0); // +1%

            preCloseMap.put("000905.SH", 5000.0);
            priceMap.put("000905.SH", 5025.0); // +50基点

            // 其他指数平盘
            preCloseMap.put("399317.SZ", 5000.0);
            priceMap.put("399317.SZ", 5000.0);

            preCloseMap.put("000852.SH", 6000.0);
            priceMap.put("000852.SH", 6000.0);

            preCloseMap.put("399006.SZ", 2000.0);
            priceMap.put("399006.SZ", 2000.0);

            preCloseMap.put("000688.SH", 1000.0);
            priceMap.put("000688.SH", 1000.0);

            // 第1步：计算综合分数
            int compositeScore = RiskMarketIndexEnum.calculateCompositeScore(today, priceMap, preCloseMap);
            log.info("第1步_计算综合分数|step1_composite_score,compositeScore={}", compositeScore);

            // 第2步：转换为百分制得分
            int percentageScore = MarketSentimentScorer.convertToPercentageScore(compositeScore);
            log.info("第2步_转换百分制得分|step2_percentage_score,percentageScore={}", percentageScore);

            // 第3步：获取市场状态
            ScoreZone zone = MarketSentimentScorer.evaluate(compositeScore);
            log.info("第3步_获取市场状态|step3_market_zone,zone={}", zone.getName());

            // 第4步：获取完整评估结果
            MarketSentimentScorer.EvaluationResult result = 
                    MarketSentimentScorer.evaluateWithDetails(compositeScore);
            log.info("第4步_完整评估结果|step4_full_result,summary={}", result.getSummary());

            // 验证流程一致性
            assertEquals(zone, result.getZone(), "直接评估和详细评估的区间应一致");
            assertEquals(percentageScore, result.getPercentageScore(), "转换得分和详细评估得分应一致");

            // 验证结果合理性（部分上涨应该在偏强区）
            assertTrue(compositeScore > 0, "部分上涨场景综合分数应为正");
            assertTrue(percentageScore >= 50, "部分上涨场景得分应不低于50");
        }

        @Test
        @DisplayName("验证评分系统数学一致性")
        void testMathematicalConsistency() {
            // 实现思路：
            // 验证评分公式在各区间边界的连续性

            // 边界点1：50对应75分
            int score50 = ScoreZone.STRONG_BULLISH.calculateScore(50);
            int score49 = ScoreZone.MILDLY_BULLISH.calculateScore(49);
            log.debug("边界验证|boundary_check,score50={},score49={}", score50, score49);
            assertTrue(score50 >= score49, "50边界处强势区得分应不低于震荡偏强区");

            // 边界点2：0对应50分
            int score0Bullish = ScoreZone.MILDLY_BULLISH.calculateScore(0);
            log.debug("中性点验证|neutral_check,score0={}", score0Bullish);
            assertEquals(50, score0Bullish, "0平衡点应得50分");

            // 边界点3：-50对应25分
            int scoreMinus50 = ScoreZone.MILDLY_BEARISH.calculateScore(-50);
            int scoreMinus51 = ScoreZone.STRONG_BEARISH.calculateScore(-51);
            log.debug("边界验证|boundary_check,scoreMinus50={},scoreMinus51={}", scoreMinus50, scoreMinus51);
            assertEquals(25, scoreMinus50, "-50边界应得25分");
            assertTrue(scoreMinus50 >= scoreMinus51, "-50边界处震荡偏弱区得分应不低于弱势区");

            log.info("评分系统数学一致性验证通过|mathematical_consistency_passed");
        }
    }
}
