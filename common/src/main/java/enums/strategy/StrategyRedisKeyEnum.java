package enums.strategy;

import lombok.Getter;

/**
 * 策略 Redis Key 枚举
 *
 * 设计目的：
 * 1. 统一管理各策略的Redis Key前缀、TTL、历史天数等配置。
 * 2. 便于扩展新策略，只需新增枚举项。
 * 3. 与StrategyMetaEnum配合，形成完整的策略元信息体系。
 *
 * 为什么使用枚举而非常量类：
 * - 枚举天然支持分组，每个策略的配置内聚在一起
 * - 类型安全，避免传错字符串
 * - 易于遍历和查找
 *
 * Redis Key命名规范：
 * - 格式：{STRATEGY_ID}:{PURPOSE}:{yyyyMMdd}
 * - 示例：NINE_TURN:PREHEAT:20260102
 *
 * @author hli
 * @date 2026-01-02
 */
@Getter
public enum StrategyRedisKeyEnum {

    /**
     * 九转序列策略预热数据
     *
     * Redis数据结构：
     * - Key: NINE_TURN:PREHEAT:{yyyyMMdd}
     * - 数据类型: Hash
     * - Field: windCode (股票代码)
     * - Value: JSON数组，存储前20个交易日收盘价，索引0=昨日收盘价
     */
    NINE_TURN_PREHEAT(
            "NINE_TURN",
            "PREHEAT",
            24,        // TTL: 24小时
            20         // 历史天数: 20天
    );


    // ===================== 预留扩展示例 =====================
    // 未来新增策略只需添加枚举项：
    // MACD_PREHEAT("MACD", "PREHEAT", 48, 30),
    // RSI_PREHEAT("RSI", "PREHEAT", 48, 14),

    /**
     * 策略ID（与StrategyMetaEnum对应）
     */
    private final String strategyId;

    /**
     * 用途标识（PREHEAT=预热, MATCH=匹配结果）
     */
    private final String purpose;

    /**
     * Redis TTL（小时）
     */
    private final int ttlHours;

    /**
     * 策略所需的历史天数
     */
    private final int historyDays;

    StrategyRedisKeyEnum(String strategyId, String purpose, int ttlHours, int historyDays) {
        this.strategyId = strategyId;
        this.purpose = purpose;
        this.ttlHours = ttlHours;
        this.historyDays = historyDays;
    }

    /**
     * 构建完整的Redis Key
     *
     * @param dateSuffix 日期后缀，格式 yyyyMMdd
     * @return 完整Key，如 NINE_TURN:PREHEAT:20260102
     */
    public String buildKey(String dateSuffix) {
        return String.format("%s:%s:%s", strategyId, purpose, dateSuffix);
    }

    /**
     * 获取Key前缀（不含日期）
     *
     * @return Key前缀，如 NINE_TURN:PREHEAT:
     */
    public String getKeyPrefix() {
        return String.format("%s:%s:", strategyId, purpose);
    }

    /**
     * 根据策略ID和用途查找枚举
     *
     * @param strategyId 策略ID
     * @param purpose    用途
     * @return 匹配的枚举，未找到返回null
     */
    public static StrategyRedisKeyEnum find(String strategyId, String purpose) {
        for (StrategyRedisKeyEnum e : values()) {
            if (e.strategyId.equalsIgnoreCase(strategyId) && e.purpose.equalsIgnoreCase(purpose)) {
                return e;
            }
        }
        return null;
    }
}
