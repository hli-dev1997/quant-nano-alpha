package util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数学计算工具类
 *
 * 职责：提供数值位移与精度格式化等基础数学处理能力。
 *
 * 设计目的：
 * 1. 统一数值精度处理，避免业务侧重复实现导致口径不一致。
 * 2. 将常用的小数位移与舍入逻辑封装为可复用工具。
 *
 * 为什么需要该类：
 * - 业务中存在大量数值缩放与舍入场景，需要统一规则。
 *
 * 核心实现思路：
 * - 以BigDecimal为核心，按指定小数位和舍入策略计算。
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-07-04 23:29:08
 * @description: 数学计算工具类
 */
public class MathUtil {

    /**
     * 截取小数点后N位
     *
     * 实现逻辑：
     * 1. 将字符串转换为BigDecimal。
     * 2. 根据位移方向选择除法或乘法。
     * 3. 按统一舍入规则返回结果。
     *
     * @param valueStr 待截取的数字字符串
     * @param digits   n位
     * @return 移动后N位数字
     */
    public static BigDecimal shiftDecimal(String valueStr, int digits) {
        // 实现思路：
        // 1. 按位数正负选择缩放方向。
        // 2. 统一使用BigDecimal避免精度丢失。
        BigDecimal raw = new BigDecimal(valueStr);

        if (digits >= 0) {
            BigDecimal divisor = BigDecimal.TEN.pow(digits);
            return raw.divide(divisor, 2, RoundingMode.HALF_UP);
        } else {
            BigDecimal multiplier = BigDecimal.TEN.pow(-digits);
            return raw.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * 格式化数字精度（缩放并保留指定小数位数）
     * <p>
     * formatDecimal(14235, 1, true);   // 输出：1424.0 (14235/10=1423.5, 向上舍入到1424.0)
     * formatDecimal(14235, 1, false);  // 输出：1423.5 (14235/10=1423.5, 向下截断到1423.5)
     * formatDecimal(123456789, 5, true); // 输出：1235.0 (缩放后向上舍入)
     * formatDecimal(123456789, 5, false); // 输出：1234.56789 (缩放后向下截断)
     *
     * 实现逻辑：
     * 1. 使用BigDecimal对输入进行缩放。
     * 2. 按roundUp决定舍入方向。
     * 3. 返回最终精度结果。
     *
     * @param originalValue 原始值
     * @param decimalPlaces 保留小数位数
     * @param roundUp       是否向上舍入，true为向上舍入，false为向下截断
     * @return 格式化后的数值
     */
    public static double formatDecimal(Number originalValue, int decimalPlaces, boolean roundUp) {
        // 实现思路：
        // 1. 先缩放到目标小数位范围。
        // 2. 再根据舍入策略处理整数位与小数位。
        if (originalValue == null) return 0.0;
        BigDecimal value = new BigDecimal(originalValue.toString());

        // 这里根据需求除以10的decimalPlaces次方，缩放数字
        BigDecimal divisionFactor = BigDecimal.TEN.pow(decimalPlaces);
        value = value.divide(divisionFactor, decimalPlaces + 5, RoundingMode.HALF_UP);

        if (roundUp) {
            // 整数位四舍五入
            value = value.setScale(0, RoundingMode.HALF_UP);
            // 再保留小数位，向下截断（你也可以改成四舍五入）
            value = value.setScale(decimalPlaces, RoundingMode.DOWN);
        } else {
            // 不四舍五入，直接截断小数位
            value = value.setScale(decimalPlaces, RoundingMode.DOWN);
        }
        return value.doubleValue();
    }
}

