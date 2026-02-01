package constants;

/**
 * Sentinel 资源名称常量类
 * <p>
 * 统一管理所有 Sentinel 资源名称，便于在 Dashboard 配置规则时保持一致。
 * <p>
 * 命名规范：模块名_功能名_操作
 *
 * @author hli
 * @date 2026-02-01
 */
public final class SentinelResourceConstants {

    private SentinelResourceConstants() {
        // 私有构造，禁止实例化
    }

    // ==================== 信号中心模块 ====================

    /**
     * 获取市场情绪分数
     * <p>
     * 用于风控分数查询的熔断保护
     */
    public static final String SIGNAL_CENTER_GET_MARKET_SENTIMENT = "signalCenter:getMarketSentiment";

    // ==================== 数据采集模块 ====================

    /**
     * 获取交易日历
     * <p>
     * 用于交易日历查询的限流保护
     */
    public static final String DATA_COLLECTOR_GET_TRADE_DATE = "getTradeDateListByTime";
}
