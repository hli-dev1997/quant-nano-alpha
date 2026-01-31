package integration.kafka;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka 相关常量统一管理（跨模块复用）。
 * 设计原则：
 * - 编译期常量：用于注解（如 @KafkaListener）和硬编码配置，确保编译期常量要求；
 * - 运行期元数据：通过 TopicMeta 提供 name/desc/category 等语义信息，便于业务层统一使用；
 * - 主题与元数据分离：字符串常量是“权威编码”，元数据用于展示与治理，不影响编译约束。
 */
public final class KafkaConstants {
    private KafkaConstants() {}

    // ======================== 编译期常量（注解/配置使用） ========================
    // 主题编码（股票行情）
    public static final String TOPIC_QUOTATION = "quotation";
    // 主题编码（指数行情）
    public static final String TOPIC_QUOTATION_INDEX = "quotation-index";

    // 主题编码（策略信号）- 策略引擎发送，信号中心消费
    public static final String TOPIC_STRATEGY_SIGNAL = "stock-strategy-signal";

    // 主题编码（各服务日志流）
    public static final String TOPIC_LOG_SERVICE_ORDER = "log-service-order";
    public static final String TOPIC_LOG_QUANT_XXL_JOB = "log-quant-xxl-job";
    public static final String TOPIC_LOG_QUANT_DATA_COLLECTOR = "log-quant-data-collector";
    public static final String TOPIC_LOG_QUANT_STRATEGY_ENGINE = "log-quant-strategy-engine";
    public static final String TOPIC_LOG_QUANT_RISK_CONTROL = "log-quant-risk-control";
    public static final String TOPIC_LOG_QUANT_SIGNAL_CENTER = "log-quant-signal-center"; // 新增：信号中心服务日志
    public static final String TOPIC_LOG_QUANT_STOCK_LIST = "log-quant-stock-list"; // 新增：股票列表服务日志
    public static final String TOPIC_LOG_QUANT_DATA_ARCHIVE = "log-quant-data-archive";

    // 消费组（审计服务）
    public static final String GROUP_DATA_ARCHIVE = "data-archive-group";
    // 消费组（风控服务）
    public static final String GROUP_RISK_CONTROL = "risk-control-group";
    // 消费组（信号中心）
    public static final String GROUP_SIGNAL_CENTER = "signal-center-group";
    // 可按需扩展：public static final String GROUP_STRATEGY_ENGINE = "strategy-engine-group";

    // Bean 名称
    public static final String LISTENER_CONTAINER_FACTORY = "kafkaListenerContainerFactory";

    // ======================== Logback Kafka Appender 常量 ========================
    /** 日志主题前缀（各服务日志主题名 = LOG_TOPIC_PREFIX + 服务名） */
    public static final String LOG_TOPIC_PREFIX = "log-";
    
    /** Kafka Appender 名称（用于动态配置更新） */
    public static final String KAFKA_APPENDER_NAME = "kafkaAppender";
    
    /** Logback 属性名：主机IP */
    public static final String LOGBACK_PROP_HOST_IP = "hostIp";
    
    /** Logback 属性名：主机名 */
    public static final String LOGBACK_PROP_HOST_NAME = "HOST_NAME";
    
    /** Logback 属性名：服务端口 */
    public static final String LOGBACK_PROP_HOST_PORT = "hostPort";
    
    /** Logback 属性名：服务名 */
    public static final String LOGBACK_PROP_SERVICE = "service";
    
    /** Logback 属性名：环境 */
    public static final String LOGBACK_PROP_ENV = "env";
    
    /** Logback 属性名：主题 */
    public static final String LOGBACK_PROP_TOPIC = "topic";

    // ======================== 运行期元数据（展示/治理） ========================
    /** 主题元数据对象：封装 code/name/desc/category 等信息 */
    public static final class TopicMeta {
        private final String code;
        private final String displayName;
        private final String desc;
        private final KafkaTopics.Category category;

        public TopicMeta(String code, String displayName, String desc, KafkaTopics.Category category) {
            this.code = code;
            this.displayName = displayName;
            this.desc = desc;
            this.category = category;
        }
        public String code() { return code; }
        public String displayName() { return displayName; }
        public String desc() { return desc; }
        public KafkaTopics.Category category() { return category; }
        public boolean isProducer() { return category == KafkaTopics.Category.PRODUCER || category == KafkaTopics.Category.BOTH; }
        public boolean isConsumer() { return category == KafkaTopics.Category.CONSUMER || category == KafkaTopics.Category.BOTH; }
    }

    /**
     * 主题元数据注册表（按物理主题名索引）。
     * 注意：此为运行期数据，供业务层使用；注解仍引用上面的编译期字符串常量。
     */
    public static final Map<String, TopicMeta> TOPIC_META_REGISTRY;
    static {
        Map<String, TopicMeta> m = new LinkedHashMap<>();
        // 股票行情主题
        m.put(TOPIC_QUOTATION, new TopicMeta(
                TOPIC_QUOTATION,
                "股票分时行情",
                "股票行情数据主题，供策略模块消费",
                KafkaTopics.Category.BOTH
        ));
        // 指数行情主题
        m.put(TOPIC_QUOTATION_INDEX, new TopicMeta(
                TOPIC_QUOTATION_INDEX,
                "指数分时行情",
                "指数行情数据主题，供风控模块计算市场情绪",
                KafkaTopics.Category.BOTH
        ));
        // 日志主题：各服务产生应用日志
        m.put(TOPIC_LOG_SERVICE_ORDER, new TopicMeta(
                TOPIC_LOG_SERVICE_ORDER,
                "服务订单日志",
                "service-order 服务运行日志消息，用于集中收集与分析",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_XXL_JOB, new TopicMeta(
                TOPIC_LOG_QUANT_XXL_JOB,
                "调度中心日志",
                "quant-xxl-job 服务运行日志与调度事件",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_DATA_COLLECTOR, new TopicMeta(
                TOPIC_LOG_QUANT_DATA_COLLECTOR,
                "数据采集服务日志",
                "quant-data-collector 服务运行日志与采集任务记录",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_STRATEGY_ENGINE, new TopicMeta(
                TOPIC_LOG_QUANT_STRATEGY_ENGINE,
                "策略引擎服务日志",
                "quant-strategy-engine 服务运行日志与策略执行记录",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_RISK_CONTROL, new TopicMeta(
                TOPIC_LOG_QUANT_RISK_CONTROL,
                "风控服务日志",
                "quant-risk-control 服务运行日志与风控事件",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_SIGNAL_CENTER, new TopicMeta(
                TOPIC_LOG_QUANT_SIGNAL_CENTER,
                "信号中心服务日志",
                "quant-signal-center 服务运行日志与信号处理事件",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_STOCK_LIST, new TopicMeta(
                TOPIC_LOG_QUANT_STOCK_LIST,
                "股票列表服务日志",
                "quant-stock-list 服务运行日志与列表管理事件",
                KafkaTopics.Category.BOTH
        ));
        m.put(TOPIC_LOG_QUANT_DATA_ARCHIVE, new TopicMeta(
                TOPIC_LOG_QUANT_DATA_ARCHIVE,
                "数据归档服务日志",
                "quant-data-archive 服务运行日志与归档事件",
                KafkaTopics.Category.BOTH
        ));
        TOPIC_META_REGISTRY = Collections.unmodifiableMap(m);
    }
}
