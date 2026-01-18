package enums.market;

import lombok.Getter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 市场核心观测指数枚举
 * 用于计算“综合市场情绪分数”，作为风控微服务的核心依据。
 * 综合分数 = Σ(指数日内涨跌幅 * 指数权重)
 */
@Getter
public enum RiskMarketIndexEnum {

    // ==================== 核心加权指数（参与综合分数计算） ====================
    /**
     * 【市场水温计】国证A指 - 全市场基准
     * 替代中证全指(000985.CSI)，覆盖沪深两市全部A股，是判断市场整体普涨/普跌最准确的基准。
     */
    GZ_A_INDEX("399317.SZ", "国证A指", "全市场基准：综合反映沪深两市全部A股的价格变动", 0.25, "2010-01-01"),
    /**
     * 【大盘价值】沪深300 - 核心资产
     * 反映A股核心资产与宏观经济基本面，是市场压舱石和最重要的衍生品标的。
     */
    CSI_300("000300.SH", "沪深300", "大盘价值：沪深两市规模最大、流动性最好的300只股票，代表核心资产", 0.25, "2005-04-08"),
    /**
     * 【中盘成长】中证500 - 中坚力量
     * 代表剔除大盘股后的中坚力量，行业分布均衡，是观察经济结构转型的关键。
     */
    CSI_500("000905.SH", "中证500", "中盘成长：剔除沪深300后，总市值排名前500的股票，代表中盘成长风格", 0.20, "2007-01-15"),
    /**
     * 【小盘弹性】中证1000 - 广度和弹性
     * 反映小市值公司整体表现，市场广度指标，波动大、弹性高，用于观测市场风险偏好。
     */
    CSI_1000("000852.SH", "中证1000", "小盘弹性：成分股市值排名在800名以后，代表小盘股和市场的广度", 0.15, "2014-10-17"),
    /**
     * 【创业成长】创业板指 - 创新成长板块
     * 代表创业板市场最具代表性的100家高科技、高成长公司，是观察市场成长风格和风险偏好的主战场。
     */
    CHINEXT("399006.SZ", "创业板指", "创业成长：创业板市场最具代表性的100家公司，聚焦高成长新兴产业", 0.10, "2010-06-01"),
    /**
     * 【硬科技先锋】科创50 - 科技战略板块
     * 聚焦科创板核心硬科技公司（半导体、信息技术等），波动大，产业趋势明确。
     * 注意：该指数自2020-07-23起发布并生效，此前数据需特殊处理。
     */
    STAR_50("000688.SH", "科创50", "硬科技先锋：科创板中市值大、流动性好的50只证券，聚焦国家战略科技领域", 0.05, "2020-07-23"),

    // ==================== 冗余观察指数（权重0%，仅用于辅助观察或特定分析） ====================
    /**
     * 【沪市传统基准】上证指数 - 历史与情绪指标
     * 历史最悠久，市场关注度极高，但规则特殊（含B股、总股本加权），作为投资基准精准度不足。
     * 可替代性：其“全市场”功能已被国证A指(399317.SZ)替代。
     */
    SHANGHAI_COMPOSITE("000001.SH", "上证指数", "沪市传统基准：上海证券交易所全部上市股票（历史情绪指标，权重0%）", 0.0, "1991-07-15"),
    /**
     * 【超级大盘】上证50 - 金融权重观察
     * 沪市超级大盘股，金融股权重极高，风格是沪深300的“金融加强版”。
     * 可替代性：其成分股和风格几乎完全被沪深300(000300.SH)覆盖。
     */
    SSE_50("000016.SH", "上证50", "超级大盘：上海证券交易所规模最大的50只股票，金融股权重高", 0.0, "2004-01-02"),
    /**
     * 【深市样本观察】深证成指 - 深市部分样本
     * 仅代表深市500只样本股，且为价格加权法，投资参考价值有限。
     * 可替代性：其代表性远弱于创业板指(399006.SZ)和跨市场指数。
     */
    SHENZHEN_COMPONENT("399001.SZ", "深证成指", "深市样本观察：深圳证券交易所市值大、流动性好的500家公司（价格加权）", 0.0, "1995-01-23");

    private final String code;        // 指数代码
    private final String name;        // 指数名称
    private final String description; // 指标说明（观察什么）
    private final double defaultWeight; // 默认权重（百分比的小数形式）
    private final String effectiveDate; // 指数生效/发布日期（用于历史回测）

    RiskMarketIndexEnum(String code, String name, String description, double defaultWeight, String effectiveDate) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.defaultWeight = defaultWeight;
        this.effectiveDate = effectiveDate;
    }

    /**
     * 根据给定日期，获取在该日期生效且需要加权的核心指数列表。
     * 这是风控计算的核心方法，自动处理了指数发布时间不同的问题。
     *
     * @param targetDate 目标日期（用于历史回测或实时计算）
     * @return 在该日期已生效且权重>0的指数枚举列表
     */
    public static List<RiskMarketIndexEnum> getWeightedIndicesForDate(LocalDate targetDate) {
        return Arrays.stream(values())
                .filter(index -> index.getDefaultWeight() > 0) // 只取参与加权的核心指数
                .filter(index -> !targetDate.isBefore(LocalDate.parse(index.getEffectiveDate()))) // 过滤出已生效的指数
                .collect(Collectors.toList());
    }

    /**
     * 计算给定日期的“综合市场情绪分数”。
     * 建议在获取批量行情数据后调用此方法。
     *
     * @param targetDate     计算日期
     * @param indexPriceMap  一个Map，key为指数代码，value为当日收盘价（或实时价）
     * @param indexPreCloseMap 一个Map，key为指数代码，value为昨日收盘价
     * @return 加权综合涨跌幅（即综合市场情绪分数）
     */
    public static double calculateCompositeScore(LocalDate targetDate,
                                                 Map<String, Double> indexPriceMap,
                                                 Map<String, Double> indexPreCloseMap) {
        List<RiskMarketIndexEnum> validIndices = getWeightedIndicesForDate(targetDate);

        double totalWeight = validIndices.stream().mapToDouble(RiskMarketIndexEnum::getDefaultWeight).sum();
        if (totalWeight == 0) return 0.0;

        double compositeScore = 0.0;
        for (RiskMarketIndexEnum index : validIndices) {
            Double currentPrice = indexPriceMap.get(index.getCode());
            Double preClose = indexPreCloseMap.get(index.getCode());

            if (currentPrice != null && preClose != null && preClose != 0) {
                double dailyChange = (currentPrice - preClose) / preClose;
                // 使用归一化权重，确保即使部分指数数据缺失，总和也为1
                compositeScore += dailyChange * (index.getDefaultWeight() / totalWeight);
            }
        }
        return compositeScore;
    }
}