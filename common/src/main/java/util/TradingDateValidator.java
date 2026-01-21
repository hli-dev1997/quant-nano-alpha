package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 交易日校验工具类
 *
 * 设计目的：
 * 1. 提供统一的交易日校验入口，避免各模块重复实现。
 * 2. 提供收盘时间校验，用于策略判断。
 *
 * 为什么放在 common 模块：
 * - 交易日校验是跨模块的通用需求（data-collector、strategy-engine 都需要）。
 * - 统一维护逻辑，避免代码重复。
 *
 * 使用说明：
 * - 需要配合 DateCache 使用，调用前需确保 DateCache 已初始化。
 * - 策略模块通过 Feign 或直接引用使用。
 *
 * @author hli
 * @date 2026-01-20
 */
public class TradingDateValidator {

    /**
     * 收盘时间：15:00:00
     */
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 0, 0);

    /**
     * 判断指定日期是否为交易日
     *
     * @param date           待校验的日期
     * @param tradeDateList  交易日历列表
     * @return true-是交易日，false-非交易日
     */
    public static boolean isTradingDay(LocalDate date, List<LocalDate> tradeDateList) {
        if (date == null || tradeDateList == null || tradeDateList.isEmpty()) {
            return false;
        }
        return tradeDateList.contains(date);
    }

    /**
     * 判断指定时间是否已收盘（>= 15:00:00）
     *
     * @param dateTime 待校验的时间
     * @return true-已收盘，false-未收盘
     */
    public static boolean isMarketClosed(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        LocalTime localTime = dateTime.toLocalTime();
        return !localTime.isBefore(MARKET_CLOSE_TIME);
    }

    /**
     * 判断是否为有效的收盘价数据
     * 满足条件：是交易日 且 时间 >= 15:00
     *
     * @param dateTime      交易时间
     * @param tradeDateList 交易日历列表
     * @return true-有效收盘价数据，false-无效
     */
    public static boolean isValidClosingPrice(LocalDateTime dateTime, List<LocalDate> tradeDateList) {
        if (dateTime == null) {
            return false;
        }
        return isTradingDay(dateTime.toLocalDate(), tradeDateList) && isMarketClosed(dateTime);
    }
}
