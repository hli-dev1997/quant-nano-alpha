package util;


import constants.DateTimeFormatConstants;
import lombok.extern.slf4j.Slf4j;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author LiHao
 * @description: 时间工具类
 * @Date 2022-09-23 13:36:56
 */
@Slf4j
public final class DateUtil {

    // 常用日期格式常量
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    // 股市收盘时间常量
    private static final LocalTime STOCK_CLOSING_TIME = LocalTime.of(15, 30);

    // 线程安全的DateFormat缓存，避免重复创建SimpleDateFormat对象
    private static final Map<String, ThreadLocal<SimpleDateFormat>> DATE_FORMAT_CACHE = new ConcurrentHashMap<>();

    /**
     * 标准日期时间格式化器 (线程安全)
     * 建议优先使用此常量，而非每次调用 DateTimeFormatter.ofPattern()
     */
    public static final DateTimeFormatter STANDARD_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATETIME_FORMAT);

    /**
     * 标准日期格式化器 (线程安全)
     */
    public static final DateTimeFormatter STANDARD_DATE_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);

    /**
     * 紧凑型日期格式化器 (线程安全)
     */
    public static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    // 私有构造函数，防止实例化
    private DateUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 获取线程安全的SimpleDateFormat实例
     * 使用ThreadLocal确保线程安全，并缓存格式化器以提高性能
     *
     * @param pattern 日期格式模式
     * @return 线程安全的SimpleDateFormat实例
     */
    private static SimpleDateFormat getDateFormat(String pattern) {
        return DATE_FORMAT_CACHE.computeIfAbsent(pattern,
                p -> ThreadLocal.withInitial(() -> new SimpleDateFormat(p))
        ).get();
    }

    // ==================== 日期格式化方法 ====================

    /**
     * 将Date对象格式化为指定格式的字符串
     * 线程安全的日期格式化方法
     *
     * @param date    待格式化的日期对象，可以为null
     * @param pattern 日期格式模式，如"yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的日期字符串，如果date为null则返回null
     * @throws IllegalArgumentException 如果pattern为空或无效
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Date pattern cannot be null or empty");
        }

        try {
            return getDateFormat(pattern).format(date);
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_format_date:_{}_with_pattern:_{}", date, pattern, e);
            throw new IllegalArgumentException("Invalid date pattern: " + pattern, e);
        }
    }

    /**
     * 将Date对象格式化为默认格式(yyyy-MM-dd HH:mm:ss)
     *
     * @param date 待格式化的日期对象
     * @return 格式化后的日期字符串
     */
    public static String formatDateTime(Date date) {
        return formatDate(date, DEFAULT_DATETIME_FORMAT);
    }

    /**
     * 将Date对象格式化为日期格式(yyyy-MM-dd)
     *
     * @param date 待格式化的日期对象
     * @return 格式化后的日期字符串
     */
    public static String formatDateOnly(Date date) {
        return formatDate(date, DEFAULT_DATE_FORMAT);
    }

    /**
     * 使用LocalDateTime格式化日期时间
     * 推荐使用此方法，性能更好且线程安全
     *
     * @param localDateTime 本地日期时间对象
     * @param pattern       格式模式
     * @return 格式化后的字符串
     */
    public static String formatLocalDateTime(LocalDateTime localDateTime, String pattern) {
        if (localDateTime == null) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Date pattern cannot be null or empty");
        }

        try {
            return localDateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_format_LocalDateTime:_{}_with_pattern:_{}", localDateTime, pattern, e);
            throw new IllegalArgumentException("Invalid date pattern: " + pattern, e);
        }
    }

    /**
     * 使用LocalDate格式化日期
     * 推荐使用此方法，性能好且线程安全
     *
     * @param localDate 本地日期对象
     * @param pattern   格式模式，例如：yyyy-MM-dd 或 yyyyMMdd
     * @return 格式化后的字符串
     */
    public static String formatLocalDate(LocalDate localDate, String pattern) {
        if (localDate == null) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Date pattern cannot be null or empty");
        }

        try {
            return localDate.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_format_LocalDate:_{}_with_pattern:_{}", localDate, pattern, e);
            throw new IllegalArgumentException("Invalid date pattern: " + pattern, e);
        }
    }

    // ==================== 日期解析方法 ====================

    /**
     * 将字符串解析为Date对象
     * 线程安全的日期解析方法
     *
     * @param dateStr 日期字符串
     * @param pattern 日期格式模式
     * @return 解析后的Date对象
     * @throws IllegalArgumentException 如果解析失败
     */
    public static Date parseDate(String dateStr, String pattern) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Date pattern cannot be null or empty");
        }

        try {
            return getDateFormat(pattern).parse(dateStr);
        } catch (ParseException e) {
//            log.error("日志记录|Log_message,Failed_to_parse_date_string:_{}_with_pattern:_{}", dateStr, pattern, e);
            throw new IllegalArgumentException("Invalid date string: " + dateStr + " for pattern: " + pattern, e);
        }
    }

    /**
     * 智能解析日期字符串
     * 自动识别常见的日期格式并解析
     *
     * @param dateStr 日期字符串
     * @return 解析后的Date对象
     * @throws IllegalArgumentException 如果无法解析
     */
    public static Date parseSmartDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // 常见日期格式列表，按使用频率排序
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd",
                "yyyyMMdd",
                "yyyyMMddHHmmss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS"
        };

        for (String pattern : patterns) {
            try {
                return parseDate(dateStr, pattern);
            } catch (IllegalArgumentException e) {
                // 继续尝试下一个格式
//                log.debug("日志记录|Log_message,Failed_to_parse_with_pattern_{}:_{}", pattern, e.getMessage());
            }
        }

        throw new IllegalArgumentException("Unable to parse date string: " + dateStr);
    }

    /**
     * 将字符串解析为LocalDateTime对象
     * 推荐使用此方法，性能更好
     *
     * @param dateStr 日期字符串
     * @param pattern 日期格式模式
     * @return 解析后的LocalDateTime对象
     */
    public static LocalDateTime parseLocalDateTime(String dateStr, String pattern) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Date pattern cannot be null or empty");
        }

        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
//            log.error("日志记录|Log_message,Failed_to_parse_LocalDateTime:_{}_with_pattern:_{}", dateStr, pattern, e);
            throw new IllegalArgumentException("Invalid date string: " + dateStr + " for pattern: " + pattern, e);
        }
    }

    // ==================== 日期计算方法 ====================

    /**
     * 日期加减运算（推荐方法）
     * 使用LocalDateTime进行计算，避免时区问题
     *
     * @param date   基准日期
     * @param amount 增减数量（正数为增加，负数为减少）
     * @param unit   时间单位（年、月、日、时、分、秒等）
     * @return 计算后的日期
     */
    public static Date addTime(Date date, long amount, ChronoUnit unit) {
        if (date == null) {
            return null;
        }

        try {
            LocalDateTime localDateTime = date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            LocalDateTime result = localDateTime.plus(amount, unit);

            return Date.from(result.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_add_time_to_date:_{},_amount:_{},_unit:_{}", date, amount, unit, e);
            throw new IllegalArgumentException("Date calculation failed", e);
        }
    }

    /**
     * 增加天数
     *
     * @param date 基准日期
     * @param days 增加的天数（可以为负数）
     * @return 计算后的日期
     */
    public static Date addDays(Date date, int days) {
        return addTime(date, days, ChronoUnit.DAYS);
    }

    /**
     * 增加小时
     *
     * @param date  基准日期
     * @param hours 增加的小时数（可以为负数）
     * @return 计算后的日期
     */
    public static Date addHours(Date date, int hours) {
        return addTime(date, hours, ChronoUnit.HOURS);
    }

    /**
     * 增加分钟
     *
     * @param date    基准日期
     * @param minutes 增加的分钟数（可以为负数）
     * @return 计算后的日期
     */
    public static Date addMinutes(Date date, int minutes) {
        return addTime(date, minutes, ChronoUnit.MINUTES);
    }

    // ==================== 日期比较方法 ====================

    /**
     * 判断日期是否在指定范围内（包含边界）
     *
     * @param date      待检查的日期
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 如果在范围内返回true，否则返回false
     */
    public static boolean isBetween(Date date, Date startDate, Date endDate) {
        if (date == null || startDate == null || endDate == null) {
            return false;
        }

        return !date.before(startDate) && !date.after(endDate);
    }

    /**
     * 判断日期是否在指定范围内（只比较日期部分，忽略时间）
     *
     * @param date      待检查的日期
     * @param startDate 开始日期字符串(yyyy-MM-dd格式)
     * @param endDate   结束日期字符串(yyyy-MM-dd格式)
     * @return 如果在范围内返回true，否则返回false
     */
    public static boolean isBetweenDates(Date date, String startDate, String endDate) {
        if (date == null || startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
            return false;
        }
        try {
            Date start = parseDate(startDate, DEFAULT_DATE_FORMAT);
            Date end = parseDate(endDate, DEFAULT_DATE_FORMAT);

            // 将时间部分设置为当天的开始和结束
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(start);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(end);
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            return isBetween(date, startCal.getTime(), endCal.getTime());
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_check_date_range_for_date:_{},_start:_{},_end:_{}", date, startDate, endDate, e);
            return false;
        }
    }

    /**
     * 比较两个日期的大小（忽略时间部分）
     *
     * @param date1 第一个日期
     * @param date2 第二个日期
     * @return 负数表示date1小于date2，0表示相等，正数表示date1大于date2
     */
    public static int compareDateOnly(Date date1, Date date2) {
        if (date1 == null && date2 == null) {
            return 0;
        }
        if (date1 == null) {
            return -1;
        }
        if (date2 == null) {
            return 1;
        }

        LocalDate localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        return localDate1.compareTo(localDate2);
    }

    // ==================== 日期差值计算方法 ====================

    /**
     * 计算两个日期之间的差值
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param unit      时间单位
     * @return 时间差值（可能为负数）
     */
    public static long getTimeDifference(Date startDate, Date endDate, ChronoUnit unit) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }

        LocalDateTime start = startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime end = endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        return unit.between(start, end);
    }

    /**
     * 计算两个日期之间相差的天数
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 相差天数（可能为负数）
     */
    public static long getDaysBetween(Date startDate, Date endDate) {
        return getTimeDifference(startDate, endDate, ChronoUnit.DAYS);
    }

    /**
     * 计算两个日期之间相差的小时数
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 相差小时数（可能为负数）
     */
    public static long getHoursBetween(Date startDate, Date endDate) {
        return getTimeDifference(startDate, endDate, ChronoUnit.HOURS);
    }

    // ==================== 业务相关方法 ====================

    /**
     * 判断当前时间是否为股市收盘后
     * 科创板等全部数据需要等到15:30才能获取完整
     *
     * @return 如果当前时间晚于15:30返回true，否则返回false
     */
    public static boolean isAfterStockClosing() {
        LocalTime now = LocalTime.now();
        boolean result = now.isAfter(STOCK_CLOSING_TIME);
//        log.debug("日志记录|Log_message,Current_time:_{},_Stock_closing_time:_{},_Is_after_closing:_{}",
//                now, STOCK_CLOSING_TIME, result);
        return result;
    }

    /**
     * 判断指定日期是否为工作日（周一到周五）
     *
     * @param date 待检查的日期
     * @return 如果是工作日返回true，否则返回false
     */
    public static boolean isWorkday(Date date) {
        if (date == null) {
            return false;
        }

        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        DayOfWeek dayOfWeek = localDate.getDayOfWeek();

        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 获取下一个工作日
     *
     * @param date 基准日期
     * @return 下一个工作日
     */
    public static Date getNextWorkday(Date date) {
        if (date == null) {
            return null;
        }

        Date nextDay = addDays(date, 1);
        while (!isWorkday(nextDay)) {
            nextDay = addDays(nextDay, 1);
        }

        return nextDay;
    }

    // ==================== 当前时间获取方法 ====================

    /**
     * 获取当前日期时间字符串
     *
     * @param pattern 格式模式
     * @return 格式化后的当前时间字符串
     */
    public static String getCurrentDateTime(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = DEFAULT_DATETIME_FORMAT;
        }
        return formatLocalDateTime(LocalDateTime.now(), pattern);
    }


    /**
     * 根据指定格式str获取当前时间
     *
     * @param format 日期格式
     * @return 指定格式的当前时间
     */
    public static String getCurrentDateTimeByStr(String format) {
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        Date date = new Date();
        return formatter.format(date);
    }

    /**
     * 获取当前日期字符串(yyyy-MM-dd格式)
     *
     * @return 当前日期字符串
     */
    public static String getCurrentDate() {
        return getCurrentDateTime(DEFAULT_DATE_FORMAT);
    }

    /**
     * 获取当前时间字符串(HH:mm:ss格式)
     *
     * @return 当前时间字符串
     */
    public static String getCurrentTime() {
        return getCurrentDateTime(DEFAULT_TIME_FORMAT);
    }

    // ==================== 特殊格式处理方法 ====================

    /**
     * 将紧凑格式的日期时间字符串转换为标准格式
     * 例如：20190412152957 -> 2019-04-12 15:29:57
     *
     * @param compactDateTime 紧凑格式的日期时间字符串(yyyyMMddHHmmss)
     * @return 标准格式的日期时间字符串
     * @throws IllegalArgumentException 如果输入格式不正确
     */
    public static String parseCompactDateTime(String compactDateTime) {
        if (compactDateTime == null || compactDateTime.isEmpty()) {
            return "";
        }

        if (compactDateTime.length() != 14) {
            throw new IllegalArgumentException("Compact date time must be 14 characters long (yyyyMMddHHmmss)");
        }

        try {
            StringBuilder result = new StringBuilder();
            result.append(compactDateTime.substring(0, 4))   // year
                    .append("-")
                    .append(compactDateTime.substring(4, 6))   // month
                    .append("-")
                    .append(compactDateTime.substring(6, 8))   // day
                    .append(" ")
                    .append(compactDateTime.substring(8, 10))  // hour
                    .append(":")
                    .append(compactDateTime.substring(10, 12)) // minute
                    .append(":")
                    .append(compactDateTime.substring(12, 14)); // second

            return result.toString();
        } catch (StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid compact date time format: " + compactDateTime, e);
        }
    }

    /**
     * 将时间戳转换为指定格式的日期字符串
     *
     * @param timestamp 时间戳（毫秒）
     * @param pattern   日期格式模式
     * @return 格式化后的日期字符串
     */
    public static String timestampToString(long timestamp, String pattern) {
        if (timestamp <= 0) {
            return "";
        }

        if (pattern == null || pattern.isEmpty()) {
            pattern = DEFAULT_DATETIME_FORMAT;
        }

        try {
            Instant instant = Instant.ofEpochMilli(timestamp);
            LocalDateTime localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return formatLocalDateTime(localDateTime, pattern);
        } catch (Exception e) {
//            log.error("日志记录|Log_message,Failed_to_convert_timestamp_{}_to_string_with_pattern_{}", timestamp, pattern, e);
            throw new IllegalArgumentException("Invalid timestamp or pattern", e);
        }
    }

    // ==================== 兼容性方法（保持向后兼容） ====================

    /**
     * @deprecated 使用 {@link #formatDate(Date, String)} 替代
     */
    @Deprecated
    public static String parseDateToString(Date date, DateFormat dateFormat) {
        if (date == null || dateFormat == null) {
            return null;
        }
        return dateFormat.format(date);
    }

    /**
     * @deprecated 使用 {@link #formatDateTime(Date)} 替代
     */
    @Deprecated
    public static String getNewFormatDateString(Date date) {
        return formatDateTime(date);
    }

    /**
     * @deprecated 使用 {@link #formatDateOnly(Date)} 替代
     */
    @Deprecated
    public static String getNewFormatDateDayString(Date date) {
        return formatDateOnly(date);
    }

    /**
     * 将 LocalDate 列表按指定格式转换为字符串列表
     *
     * @param localDates List<LocalDate> 日期列表
     * @param pattern    日期格式，如 "yyyy-MM-dd" 或 "yyyyMMdd"
     * @return List<String> 格式化后的日期字符串列表
     */
    public static List<String> formatLocalDateList(List<LocalDate> localDates, String pattern) {
        if (localDates == null || localDates.isEmpty()) {
            return List.of();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localDates.stream()
                .map(date -> date.format(formatter))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定格式下的今年第一天
     *
     * @param pattern 日期格式，例如：yyyyMMdd
     * @return 今年第一天的字符串
     */
    public static String getFirstDayOfYear(String pattern) {
        LocalDate firstDay = LocalDate.now().withDayOfYear(1);
        return firstDay.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 获取指定格式下的今年最后一天
     *
     * @param pattern 日期格式，例如：yyyyMMdd
     * @return 今年最后一天的字符串
     */
    public static String getLastDayOfYear(String pattern) {
        LocalDate lastDay = LocalDate.now().withMonth(12).withDayOfMonth(31);
        return lastDay.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 获取指定年份第一天，格式例如yyyy-MM-dd
     *
     * @param year    年份，例如2024
     * @param pattern 日期格式，例如：yyyyMMdd
     * @return 返回格式如 "2024-01-01"
     */
    public static String getFirstDayOfYear(int year, String pattern) {
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(pattern);
        return firstDay.format(FORMATTER);
    }

    /**
     * 获取指定年份最后一天，格式例如yyyy-MM-dd
     *
     * @param year    年份，例如2024
     * @param pattern 日期格式，例如：yyyyMMdd
     * @return 返回格式如 "2024-12-31"
     */
    public static String getLastDayOfYear(int year, String pattern) {
        LocalDate lastDay = LocalDate.of(year, 12, 31);
        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(pattern);
        return lastDay.format(FORMATTER);
    }

    /**
     * 通用日期格式转换方法（支持日期和时间）
     *
     * @param inputDate     输入的日期字符串
     * @param inputPattern  输入的日期格式
     * @param outputPattern 输出的目标格式
     * @return 转换后的日期字符串，失败返回 null
     */
    public static String convertDateFormat(String inputDate, String inputPattern, String outputPattern) {
        if (Objects.isNull(inputDate) || Objects.isNull(inputPattern) || Objects.isNull(outputPattern)) {
            throw new RuntimeException("参数不能为空");
        }
        try {
            // 首先尝试解析为 LocalDateTime（支持带时间格式）
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(inputPattern);
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputPattern);

            if (inputPattern.contains("H") || inputPattern.contains("m") || inputPattern.contains("s")) {
                LocalDateTime dateTime = LocalDateTime.parse(inputDate, inputFormatter);
                return dateTime.format(outputFormatter);
            } else {
                LocalDate date = LocalDate.parse(inputDate, inputFormatter);
                return date.format(outputFormatter);
            }
        } catch (DateTimeParseException e) {
            throw new RuntimeException("日期格式转换失败: " + e.getMessage());
        }
    }

    /**
     * 将指定格式的日期字符串转换为 LocalDate
     *
     * @param dateStr      日期字符串（如 20250614）
     * @param inputPattern 输入格式（如 yyyyMMdd）
     * @return 转换后的 LocalDate，失败返回 null
     */
    public static LocalDate parseToLocalDate(String dateStr, String inputPattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(inputPattern);
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            log.error("转换为LocalDate失败|LocalDate_parse_failed,dateStr={},pattern={}", dateStr, inputPattern, e);
            return null;
        }
    }

    /**
     * 时间调整方法
     *
     * @param date 需要调整时间参数
     * @param fmt  传入日期格式字符串
     * @param num  调整大小,输入整数为增加时间,负数为减少时间,单位天数
     * @return 调整后的fmt格式时间
     */
    public static String stringTimeToAdjust(String date, String fmt, Integer num) {
        if (date == null || date.isEmpty()) {
            return date;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(fmt);
        Date dt = null;
        try {
            dt = sdf.parse(date);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        Calendar nowDate = Calendar.getInstance();
        nowDate.setTime(dt);
        nowDate.add(Calendar.DAY_OF_WEEK, num);
        Date dt1 = nowDate.getTime();
        String dayString = sdf.format(dt1);
        return dayString;
    }

    /**
     * 将Unix时间戳转换为Date对象
     *
     * @param timestamp Unix时间戳（秒）
     * @return Date对象
     */
    /**
     * 将 Unix 秒级时间戳转换为北京时间格式字符串
     *
     * @param timestamp Unix 时间戳（单位：秒）
     * @return 格式化后的北京时间字符串，如 "2025-07-22 10:50:08"，非法时间戳返回 null
     */
    public static String timestampToDateStr(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        Date date = new Date(timestamp * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat(DateTimeFormatConstants.DEFAULT_DATETIME_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(DateTimeFormatConstants.SHANG_HAI)); // 设置为北京时间
        return sdf.format(date);
    }
}
