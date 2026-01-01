package constants;

/**
 * @author hli
 * @program: datacollector
 * @Date 2025-01-27
 * @description: 日期时间格式常量类
 */
public class DateTimeFormatConstants {

    /**
     * 默认日期格式
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认时间格式
     */
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    /**
     * 斜杠分隔的日期时间格式
     */
    public static final String SLASH_DATETIME_FORMAT = "yyyy/MM/dd HH:mm:ss";

    /**
     * 斜杠分隔的日期格式
     */
    public static final String SLASH_DATE_FORMAT = "yyyy/MM/dd";

    /**
     * 紧凑日期格式（无分隔符）
     */
    public static final String COMPACT_DATE_FORMAT = "yyyyMMdd";

    /**
     * 8位数字日期格式（如：20190214）
     */
    public static final String EIGHT_DIGIT_DATE_FORMAT = "yyyyMMdd";

    /**
     * 紧凑日期时间格式（无分隔符）
     */
    public static final String COMPACT_DATETIME_FORMAT = "yyyyMMddHHmmss";

    /**
     * ISO 8601 日期时间格式
     */
    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * ISO 8601 日期时间格式（带毫秒）
     */
    public static final String ISO_DATETIME_WITH_MILLIS_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    /**
     * ISO 8601 UTC 日期时间格式（带毫秒和Z后缀）
     * 用于Kafka日志、ES索引等需要UTC时间戳的场景
     */
    public static final String ISO_DATETIME_UTC_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * 中文日期格式
     */
    public static final String CHINESE_DATE_FORMAT = "yyyy年MM月dd日";

    /**
     * 中文日期时间格式
     */
    public static final String CHINESE_DATETIME_FORMAT = "yyyy年MM月dd日 HH:mm:ss";

    /**
     * 美式日期格式
     */
    public static final String US_DATE_FORMAT = "MM/dd/yyyy";

    /**
     * 欧式日期格式
     */
    public static final String EU_DATE_FORMAT = "dd/MM/yyyy";

    /**
     * 时间戳格式（毫秒）
     */
    public static final String TIMESTAMP_FORMAT = "yyyyMMddHHmmssSSS";

    /**
     * 月份格式
     */
    public static final String MONTH_FORMAT = "yyyy-MM";

    /**
     * 年份格式
     */
    public static final String YEAR_FORMAT = "yyyy";

    /**
     * 私有构造函数，防止实例化
     */
    private DateTimeFormatConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final String SHANG_HAI = "Asia/Shanghai";
}