package enums.market;

import lombok.Getter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 市场核心观测指数枚举 (Risk Market Index Enum)
 *
 * <p>用于计算"综合市场情绪分数"，作为风控微服务的核心依据。
 * 综合分数 = Σ(指数日内涨跌幅% * 指数权重%) / 总权重%
 *
 * <h3>评分标准说明 (Scoring Standard):</h3>
 * <ul>
 *   <li>权重使用百分制：25 表示 25% 权重</li>
 *   <li>综合分数使用整数百分制：150 表示综合涨跌 1.5%</li>
 *   <li>涨跌幅计算：(当前价 - 昨收价) / 昨收价 * 10000</li>
 * </ul>
 *
 * @author haoli
 */
@Getter
public enum RiskMarketIndexEnum {

    // ==================== 核心加权指数（参与综合分数计算） ====================

    /**
     * 【市场水温计】国证A指 - 全市场基准
     * 替代中证全指(000985.CSI)，覆盖沪深两市全部A股，是判断市场整体普涨/普跌最准确的基准。
     */
    GZ_A_INDEX("399317.SZ", "国证A指", "全市场基准：综合反映沪深两市全部A股的价格变动", 25, "2010-01-01"),

    /**
     * 【大盘价值】沪深300 - 核心资产
     * 反映A股核心资产与宏观经济基本面，是市场压舱石和最重要的衍生品标的。
     */
    CSI_300("000300.SH", "沪深300", "大盘价值：沪深两市规模最大、流动性最好的300只股票，代表核心资产", 25, "2005-04-08"),

    /**
     * 【中盘成长】中证500 - 中坚力量
     * 代表剔除大盘股后的中坚力量，行业分布均衡，是观察经济结构转型的关键。
     */
    CSI_500("000905.SH", "中证500", "中盘成长：剔除沪深300后，总市值排名前500的股票，代表中盘成长风格", 20, "2007-01-15"),

    /**
     * 【小盘弹性】中证1000 - 广度和弹性
     * 反映小市值公司整体表现，市场广度指标，波动大、弹性高，用于观测市场风险偏好。
     */
    CSI_1000("000852.SH", "中证1000", "小盘弹性：成分股市值排名在800名以后，代表小盘股和市场的广度", 15, "2014-10-17"),

    /**
     * 【创业成长】创业板指 - 创新成长板块
     * 代表创业板市场最具代表性的100家高科技、高成长公司，是观察市场成长风格和风险偏好的主战场。
     */
    CHINEXT("399006.SZ", "创业板指", "创业成长：创业板市场最具代表性的100家公司，聚焦高成长新兴产业", 10, "2010-06-01"),

    /**
     * 【硬科技先锋】科创50 - 科技战略板块
     * 聚焦科创板核心硬科技公司（半导体、信息技术等），波动大，产业趋势明确。
     * 注意：该指数自2020-07-23起发布并生效，此前数据需特殊处理。
     */
    STAR_50("000688.SH", "科创50", "硬科技先锋：科创板中市值大、流动性好的50只证券，聚焦国家战略科技领域", 5, "2020-07-23"),

    // ==================== 冗余观察指数（权重0%，仅用于辅助观察或特定分析） ====================

    /**
     * 【沪市传统基准】上证指数 - 历史与情绪指标
     * 历史最悠久，市场关注度极高，但规则特殊（含B股、总股本加权），作为投资基准精准度不足。
     * 可替代性：其"全市场"功能已被国证A指(399317.SZ)替代。
     */
    SHANGHAI_COMPOSITE("000001.SH", "上证指数", "沪市传统基准：上海证券交易所全部上市股票（历史情绪指标，权重0%）", 0, "1991-07-15"),

    /**
     * 【超级大盘】上证50 - 金融权重观察
     * 沪市超级大盘股，金融股权重极高，风格是沪深300的"金融加强版"。
     * 可替代性：其成分股和风格几乎完全被沪深300(000300.SH)覆盖。
     */
    SSE_50("000016.SH", "上证50", "超级大盘：上海证券交易所规模最大的50只股票，金融股权重高", 0, "2004-01-02"),

    /**
     * 【深市样本观察】深证成指 - 深市部分样本
     * 仅代表深市500只样本股，且为价格加权法，投资参考价值有限。
     * 可替代性：其代表性远弱于创业板指(399006.SZ)和跨市场指数。
     */
    SHENZHEN_COMPONENT("399001.SZ", "深证成指", "深市样本观察：深圳证券交易所市值大、流动性好的500家公司（价格加权）", 0, "1995-01-23"),

    // ==================== ETF 影子兼容项（权重0%，用于兼容数据表） ====================

    /**
     * 【兼容项】华泰柏瑞沪深300ETF
     * 对应指数：CSI_300 (000300.SH)。
     * 作用：仅用于兼容数据库中存在的ETF代码，权重为0避免重复计算。
     */
    ETF_300("510300.SH", "300ETF", "数据兼容：沪深300指数的ETF影子（权重0%）", 0, "2012-05-04"),

    /**
     * 【兼容项】易方达创业板ETF
     * 对应指数：CHINEXT (399006.SZ)。
     * 作用：仅用于兼容数据库中存在的ETF代码，权重为0避免重复计算。
     */
    ETF_CHINEXT("159915.SZ", "创业板ETF", "数据兼容：创业板指的ETF影子（权重0%）", 0, "2011-09-20");

    // ==================== 百分比转换常量 ====================

    /**
     * 百分比转换因子：将小数涨跌幅转换为百分比数值
     */
    private static final int PERCENTAGE_FACTOR = 100;

    // ==================== 枚举属性 ====================

    /**
     * 指数代码
     */
    private final String code;

    /**
     * 指数名称
     */
    private final String name;

    /**
     * 指标说明（观察什么）
     */
    private final String description;

    /**
     * 默认权重（百分制：25表示25%权重）
     */
    private final int defaultWeight;

    /**
     * 指数生效/发布日期（用于历史回测）
     */
    private final String effectiveDate;

    RiskMarketIndexEnum(String code, String name, String description, int defaultWeight, String effectiveDate) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.defaultWeight = defaultWeight;
        this.effectiveDate = effectiveDate;
    }

    // ==================== 静态缓存 (Static Cache) ====================

    /**
     * 缓存所有定义的指数代码 (Cache of all codes)
     * <p>包含核心指数、观察指数和ETF兼容项。
     * <p>使用 Java 16+ 的 Stream.toList()，生成的是不可变列表 (Immutable List)。
     * <p>外部调用：RiskMarketIndexEnum.ALL_CODES
     */
    public static final List<String> ALL_CODES = Arrays.stream(values())
            .map(RiskMarketIndexEnum::getCode)
            .toList();
    // ^^^ JDK 16+ 专属语法：极简、高效、安全

    // ==================== 核心计算方法 ====================

    /**
     * 根据给定日期，获取在该日期生效且需要加权的核心指数列表
     * Get weighted indices effective on the given date
     *
     * @param targetDate 目标日期（用于历史回测或实时计算）
     * @return 在该日期已生效且权重>0的指数枚举列表
     */
    public static List<RiskMarketIndexEnum> getWeightedIndicesForDate(LocalDate targetDate) {
        return Arrays.stream(values())
                .filter(index -> index.getDefaultWeight() > 0)
                .filter(index -> !targetDate.isBefore(LocalDate.parse(index.getEffectiveDate())))
                .collect(Collectors.toList());
    }

    /**
     * 计算给定日期的"综合市场情绪分数"（整数百分制）
     * Calculate composite market sentiment score (integer percentage scale)
     *
     * <p>返回值为整数百分制：150 = 涨跌150基点(即1.5%)，-80 = 涨跌-80基点(即-0.8%)
     *
     * @param targetDate       计算日期
     * @param indexPriceMap    一个Map，key为指数代码，value为当日收盘价（或实时价）
     * @param indexPreCloseMap 一个Map，key为指数代码，value为昨日收盘价
     * @return 加权综合涨跌幅（整数百分制，150 = 涨跌150基点）
     */
    public static int calculateCompositeScore(LocalDate targetDate,
                                              Map<String, Double> indexPriceMap,
                                              Map<String, Double> indexPreCloseMap) {
        List<RiskMarketIndexEnum> validIndices = getWeightedIndicesForDate(targetDate);

        int totalWeight = validIndices.stream().mapToInt(RiskMarketIndexEnum::getDefaultWeight).sum();
        if (totalWeight == 0) {
            return 0;
        }

        double compositeScore = 0.0;
        for (RiskMarketIndexEnum index : validIndices) {
            Double currentPrice = indexPriceMap.get(index.getCode());
            Double preClose = indexPreCloseMap.get(index.getCode());

            if (currentPrice != null && preClose != null && preClose != 0) {
                // 计算涨跌幅：(当前价 - 昨收价) / 昨收价
                double dailyChange = (currentPrice - preClose) / preClose;
                // 使用归一化权重，确保即使部分指数数据缺失，总和也为100%
                compositeScore += dailyChange * index.getDefaultWeight() / totalWeight;
            }
        }
        // 乘以 10000 转换为整数百分制：0.015 -> 150
        return (int) Math.round(compositeScore * PERCENTAGE_FACTOR * PERCENTAGE_FACTOR);
    }
}