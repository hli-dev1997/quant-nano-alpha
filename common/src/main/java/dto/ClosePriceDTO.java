package dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



/**
 * 收盘价数据传输对象
 * <p>
 * 用于策略预热时传递历史收盘价数据，包含交易日期和收盘价信息。
 * <p>
 * <b>null 值说明：</b>
 * 当 {@code closePrice} 为 null 时，表示该交易日数据不可用，可能原因：
 * <ul>
 *     <li>股票停牌</li>
 *     <li>数据缺失</li>
 *     <li>其他异常情况</li>
 * </ul>
 * 策略端应跳过 closePrice 为 null 的记录，自动使用前一个有效交易日的数据。
 *
 * @author hli
 * @date 2026-01-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePriceDTO {

    /**
     * 交易日期时间（格式：yyyy-MM-dd HH:mm:ss 字符串，保留时分秒）
     */
    private String tradeDate;

    /**
     * 收盘价
     * <p>
     * 为 null 时表示当天停牌或数据异常，策略端应跳过此日期，
     * 向前取上一个有效交易日的收盘价进行计算。
     */
    private Double closePrice;
}
