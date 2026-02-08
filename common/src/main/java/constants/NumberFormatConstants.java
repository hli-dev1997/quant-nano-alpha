package constants;

/**
 * 数字格式化常量类
 * <p>
 * 类职责：
 * 统一管理数字格式化模板，避免魔法值。
 * <p>
 * 使用场景：
 * - 日志输出价格、指标等数值
 * - 报表展示数据
 *
 * @author hli
 * @date 2026-02-08
 */
public class NumberFormatConstants {

    // ===================== 小数位格式 =====================

    /**
     * 保留 2 位小数
     * <p>
     * 示例: 12.34
     */
    public static final String DECIMAL_2 = "%.2f";

    /**
     * 保留 4 位小数
     * <p>
     * 示例: 12.3456
     */
    public static final String DECIMAL_4 = "%.4f";

    /**
     * 保留 6 位小数
     * <p>
     * 示例: 12.345678
     */
    public static final String DECIMAL_6 = "%.6f";

    // ===================== 百分比格式 =====================

    /**
     * 百分比格式（2 位小数）
     * <p>
     * 示例: 12.34%
     */
    public static final String PERCENT_2 = "%.2f%%";

    /**
     * 私有构造函数，防止实例化
     */
    private NumberFormatConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
