package constants;

/**
 * @author hli
 * @program: datacollector
 * @Date 2025-08-07 17:10:32
 * @description: RedisKey常量类
 * 规范:
 * 所有的key都应遵循 应用名_功能名_业务自定义
 */
public class RedisKeyConstants {

    /**
     * 每个交易日涨停股票代码映射关系
     * data:应用简称
     * limitUp:功能名称
     * tradingDateMappingStockMap:业务自定义
     */
    public static final String DATA_LIMIT_UP_TRADING_DATE_MAPPING_STOCK_MAP = "DATA_LIMIT_UP_TRADING_DATE_MAPPING_STOCK_MAP";

    /**
     * 每个题材映射股票代码映射关系
     * key:题材ID
     * value:对应股票代码列表
     */
    public static final String DATA_TOPIC_MAPPING_STOCK_MAP = "DATA_TOPIC_MAPPING_STOCK_MAP";

    // ==================== 风控模块 Redis Key ====================

    /**
     * 指数上一交易日收盘价缓存
     * <p>
     * Key格式: RISK:INDEX_PRE_CLOSE:{windCode}
     * Value: 收盘价（Double，JSON序列化）
     * 过期时间: 25小时（覆盖到下个交易日）
     * <p>
     * 命名规范：应用名_功能名_业务自定义
     * - RISK: 风控模块
     * - INDEX_PRE_CLOSE: 指数昨收价
     */
    public static final String RISK_INDEX_PRE_CLOSE_PREFIX = "RISK:INDEX_PRE_CLOSE:";

    // ==================== 交易日历 Redis Key ====================

    /**
     * 交易日历缓存 Key 前缀
     * <p>
     * Key格式: TRADE_DATE:CALENDAR:{year}
     * Value类型: SortedSet，member 为 yyyyMMdd 格式日期，score 为日期数值
     * 过期时间: 365天
     * <p>
     * 使用场景：
     * - data-collector 启动时写入
     * - strategy-engine 从 Redis 读取用于策略判断
     */
    public static final String TRADE_DATE_CALENDAR_PREFIX = "TRADE_DATE:CALENDAR:";

    /**
     * 全量交易日历缓存 Key
     * <p>
     * 包含所有已加载年份的交易日历（2020年至今年）
     */
    public static final String TRADE_DATE_CALENDAR_ALL = "TRADE_DATE:CALENDAR:ALL";

    // ==================== 策略模块 Redis Key ====================

    /**
     * 九转序列策略预热数据 Key 前缀
     * <p>
     * Key格式: NINE_TURN:PREHEAT:{yyyyMMdd}
     * Value类型: Hash
     * - Field: windCode (股票代码)
     * - Value: JSON数组，存储前20个交易日收盘价
     * 过期时间: 24小时
     * <p>
     * 说明：红九和绿九共用同一份历史收盘价数据，只是判断公式不同。
     */
    public static final String NINE_TURN_PREHEAT_PREFIX = "NINE_TURN:PREHEAT:";

    /**
     * 多周期均线策略预热数据 Key 前缀
     * <p>
     * Key格式: MA:PREHEAT:{yyyyMMdd}
     * Value类型: Hash
     * - Field: windCode (股票代码)
     * - Value: JSON数组，存储前59个交易日收盘价（用于计算 MA5/MA20/MA60）
     * 过期时间: 24小时
     */
    public static final String MA_PREHEAT_PREFIX = "MA:PREHEAT:";

    /**
     * DMI趋向指标策略预热数据 Key 前缀
     * <p>
     * Key格式: DMI:PREHEAT:{yyyyMMdd}
     * Value类型: Hash
     * - Field: windCode (股票代码)
     * - Value: JSON数组，存储前60个交易日的 DailyOhlcDTO（最高价、最低价、收盘价）
     * 过期时间: 24小时
     * <p>
     * 说明：用于计算 +DI、-DI 和 ADX 指标
     */
    public static final String DMI_PREHEAT_PREFIX = "DMI:PREHEAT:";

    // ==================== 信号中心 Redis Key ====================

    /**
     * 市场情绪分数缓存 Key
     * 由 quant-risk-control 更新，信号中心查询
     */
    public static final String MARKET_SENTIMENT_SCORE = "market:sentiment:score";

    /**
     * 股票信号列表缓存 Key 前缀
     * 格式：stock:signal:list:{策略名}:{交易日}
     */
    public static final String STOCK_SIGNAL_LIST_PREFIX = "stock:signal:list:";

    // ==================== 股票列表模块 Redis Key ====================

    /**
     * 股票信号查询分布式锁 Key 前缀
     * 格式：lock:stock:signal:{type}:{strategyId}:{tradeDate}
     */
    public static final String STOCK_SIGNAL_LOCK_PREFIX = "lock:stock:signal:";

    /**
     * 空值标记，用于缓存击穿保护
     * 当查询结果为空时，缓存此标记而非空列表
     */
    public static final String CACHE_EMPTY_MARKER = "__EMPTY__";
}

