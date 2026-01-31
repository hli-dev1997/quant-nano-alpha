package com.quant.data.archive.integration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.data.archive.model.es.LogDocument;
import com.quant.data.archive.repository.LogEsRepository;
import constants.DateTimeFormatConstants;
import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka日志消费服务
 *
 * 设计目的：
 * 1. 统一消费各服务推送到Kafka的日志消息。
 * 2. 解析结构化日志并形成统一的消费链路。
 *
 * 为什么需要该类：
 * - 多服务日志需要统一采集与审计，避免散落处理。
 *
 * 核心实现思路：
 * - 解析JSON日志并补齐Kafka元信息。
 * - 记录消费统计与异常日志。
 * - 提供可扩展的后续处理入口。
 *
 * <p>功能：
 * 1. 统一消费各服务推送到Kafka的日志消息
 * 2. 解析结构化日志，提取服务实例信息
 * 3. 支持日志级别过滤和异常日志特殊处理
 * 4. 提供消费统计和监控指标
 *
 * <p>设计思路（大厂面试官视角）：
 * - 结构化处理：解析JSON格式日志，提取关键字段进行分类存储
 * - 实例识别：通过IP+端口+服务名精确识别日志来源实例
 * - 性能优化：批量处理、异步确认、内存缓冲等提升吞吐量
 * - 监控友好：提供消费速率、错误率等关键指标
 * - 扩展性：预留接口支持日志存储到ES、数据库等多种后端
 */
@Slf4j
@Service
public class LogConsumerService {

    @Autowired
    private ObjectMapper objectMapper;

    /** Elasticsearch 日志 Repository */
    @Autowired
    private LogEsRepository logEsRepository;

    /** 消费计数器 */
    private final AtomicLong consumeCounter = new AtomicLong(0);
    
    /** 错误计数器 */
    private final AtomicLong errorCounter = new AtomicLong(0);

    /** 时间格式化器（使用公共常量定义的UTC格式） */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.ISO_DATETIME_UTC_FORMAT);

    @KafkaListener(
            topics = {
                    KafkaConstants.TOPIC_LOG_SERVICE_ORDER,
                    KafkaConstants.TOPIC_LOG_QUANT_XXL_JOB,
                    KafkaConstants.TOPIC_LOG_QUANT_DATA_COLLECTOR,
                    KafkaConstants.TOPIC_LOG_QUANT_STRATEGY_ENGINE,
                    KafkaConstants.TOPIC_LOG_QUANT_RISK_CONTROL,
                    KafkaConstants.TOPIC_LOG_QUANT_SIGNAL_CENTER, // 新增：信号中心服务日志
                    KafkaConstants.TOPIC_LOG_QUANT_STOCK_LIST,
                    KafkaConstants.TOPIC_LOG_QUANT_DATA_ARCHIVE
            },
            groupId = KafkaConstants.GROUP_DATA_ARCHIVE,
            containerFactory = KafkaConstants.LISTENER_CONTAINER_FACTORY
    )
    /**
     * 消费Kafka日志消息
     *
     * 实现逻辑：
     * 1. 解析日志并补齐Kafka元信息。
     * 2. 调用处理逻辑并输出统计信息。
     * 3. 根据异常类型决定是否确认或重试。
     *
     * @param record 消费记录
     * @param ack 手动确认器
     * @param topic 主题
     * @param partition 分区
     * @param offset 位点
     */
    public void consumeLog(
            ConsumerRecord<String, String> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        // 实现思路：
        // 1. 解析日志并补齐元信息。
        // 2. 处理后进行统计与确认。
        long startTime = System.currentTimeMillis();
        long currentCount = consumeCounter.incrementAndGet();
        
        try {
            String key = record.key();
            String value = record.value();
            
            // 解析结构化日志
            LogMessage logMessage = parseLogMessage(value);
            if (logMessage == null) {
                log.warn("日志解析失败|Log_parse_failed,topic={},partition={},offset={},value={}",
                        topic, partition, offset, value);
                ack.acknowledge();
                return;
            }
            
            // 增强日志信息
            logMessage.setKafkaTopic(topic);
            logMessage.setKafkaPartition(partition);
            logMessage.setKafkaOffset(offset);
            logMessage.setKafkaKey(key);
            logMessage.setConsumeTime(LocalDateTime.now());
            
            // 处理日志消息
            processLogMessage(logMessage);
            
            // 统计和监控
            long processingTime = System.currentTimeMillis() - startTime;
            if (currentCount % 1000 == 0) {
                log.info("消费统计|Consume_stats,total={},error={},costMs={}",
                        currentCount, errorCounter.get(), processingTime);
            }
            
            // 确认消费
            ack.acknowledge();
            
        } catch (Exception e) {
            errorCounter.incrementAndGet();
            log.error("日志消费异常|Log_consume_error,topic={},partition={},offset={}",
                    topic, partition, offset, e);
            
            // 根据错误类型决定是否重试
            if (shouldRetry(e)) {
                // 不确认，触发重试
                log.warn("消息将重试处理|Message_will_retry");
            } else {
                // 确认消费，避免死循环
                log.warn("消息跳过处理|Message_skip_processing");
                ack.acknowledge();
            }
        }
    }

    /**
     * 解析日志消息
     *
     * 实现逻辑：
     * 1. 解析JSON字段并映射到日志实体。
     * 2. 处理时间戳并生成实例标识。
     *
     * @param jsonValue 日志JSON字符串
     * @return 日志实体，解析失败返回null
     */
    private LogMessage parseLogMessage(String jsonValue) {
        // 实现思路：
        // 1. 解析JSON字段并填充实体。
        // 2. 处理时间戳与实例标识。
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonValue);
            
            LogMessage logMessage = new LogMessage();
            logMessage.setEnv(getTextValue(jsonNode, "env"));
            logMessage.setService(getTextValue(jsonNode, "service"));
            logMessage.setHostname(getTextValue(jsonNode, "hostname"));
            logMessage.setIp(getTextValue(jsonNode, "ip"));
            logMessage.setPort(getTextValue(jsonNode, "port"));
            logMessage.setLevel(getTextValue(jsonNode, "level"));
            logMessage.setThread(getTextValue(jsonNode, "thread"));
            logMessage.setLogger(getTextValue(jsonNode, "logger"));
            logMessage.setMessage(getTextValue(jsonNode, "message"));
            logMessage.setException(getTextValue(jsonNode, "exception"));
            
            // 解析时间戳
            String timestampStr = getTextValue(jsonNode, "timestamp");
            if (timestampStr != null && !timestampStr.isEmpty()) {
                try {
                    logMessage.setTimestamp(LocalDateTime.parse(timestampStr, TIMESTAMP_FORMATTER));
                } catch (Exception e) {
                    log.debug("时间戳解析失败|Timestamp_parse_failed,timestamp={}", timestampStr);
                    logMessage.setTimestamp(LocalDateTime.now());
                }
            } else {
                logMessage.setTimestamp(LocalDateTime.now());
            }
            
            // 生成实例标识
            logMessage.setInstanceId(generateInstanceId(logMessage));
            
            return logMessage;
            
        } catch (Exception e) {
            log.debug("JSON解析失败|Json_parse_failed,error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 安全获取 JSON 文本值
     *
     * 实现逻辑：
     * 1. 判断字段节点是否为空。
     * 2. 返回文本值或null。
     *
     * @param jsonNode JSON节点
     * @param fieldName 字段名
     * @return 字段文本
     */
    private String getTextValue(JsonNode jsonNode, String fieldName) {
        // 实现思路：
        // 1. 校验字段节点有效性。
        // 2. 读取并返回文本值。
        JsonNode fieldNode = jsonNode.get(fieldName);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }

    /**
     * 生成实例标识
     *
     * 实现逻辑：
     * 1. 优先使用服务名+IP+端口组合。
     * 2. 字段缺失时逐级降级。
     *
     * @param logMessage 日志实体
     * @return 实例标识
     */
    private String generateInstanceId(LogMessage logMessage) {
        // 实现思路：
        // 1. 多字段组合生成唯一实例ID。
        // 2. 缺失字段时使用降级组合。
        String service = logMessage.getService();
        String ip = logMessage.getIp();
        String port = logMessage.getPort();
        
        if (service != null && ip != null && port != null) {
            return String.format("%s-%s-%s", service, ip, port);
        } else if (service != null && ip != null) {
            return String.format("%s-%s", service, ip);
        } else if (service != null) {
            return service;
        } else {
            return "unknown";
        }
    }

    /**
     * 处理日志消息
     *
     * 实现逻辑：
     * 1. 输出接收日志摘要。
     * 2. 对异常日志进行额外告警记录。
     *
     * @param logMessage 日志实体
     */
    private void processLogMessage(LogMessage logMessage) {
        // 实现思路：
        // 1. 输出日志摘要与核心字段。
        // 2. 异常日志单独记录。
        // 记录接收的日志信息
        log.info("收到日志|Log_received,instanceId={},level={},timestamp={},message={}",
                logMessage.getInstanceId(), 
                logMessage.getLevel(), 
                logMessage.getTimestamp(), 
                truncateMessage(logMessage.getMessage(), 200));
        
        // 特殊处理错误日志
        if ("ERROR".equalsIgnoreCase(logMessage.getLevel()) && 
            logMessage.getException() != null && !logMessage.getException().isEmpty()) {
            
            log.warn("发现异常日志|Exception_log_detected,instanceId={},exception={}",
                    logMessage.getInstanceId(), 
                    truncateMessage(logMessage.getException(), 500));
        }
        
        // 写入 Elasticsearch
        try {
            LogDocument doc = convertToEsDoc(logMessage);
            logEsRepository.save(doc);
            log.debug("ES写入成功|ES_write_success,instanceId={}", logMessage.getInstanceId());
        } catch (Exception e) {
            // ES 写入失败不影响主业务流程，仅记录错误日志
            log.error("ES写入失败|ES_write_failed,instanceId={}", logMessage.getInstanceId(), e);
        }
        
        // TODO: [告警接入] 在此处实现基于日志级别的实时告警
        // 建议：
        // 1. 过滤 ERROR 级别日志
        // 2. 对接钉钉/飞书/邮件通知
        // 3. 实现告警抑制（防止刷屏）
    }

    /**
     * 转换 LogMessage 为 ES 文档
     *
     * 实现逻辑：
     * 1. 复制所有日志字段到 ES 文档实体
     * 2. 保留 Kafka 元信息用于追溯
     *
     * @param msg 日志消息实体
     * @return ES 文档实体
     */
    private LogDocument convertToEsDoc(LogMessage msg) {
        LogDocument doc = new LogDocument();
        doc.setService(msg.getService());
        doc.setEnv(msg.getEnv());
        doc.setLevel(msg.getLevel());
        doc.setMessage(msg.getMessage());
        doc.setException(msg.getException());
        doc.setThread(msg.getThread());
        doc.setLogger(msg.getLogger());
        doc.setHostname(msg.getHostname());
        doc.setIp(msg.getIp());
        doc.setPort(msg.getPort());
        doc.setInstanceId(msg.getInstanceId());
        doc.setTimestamp(msg.getTimestamp());
        doc.setConsumeTime(msg.getConsumeTime());
        doc.setKafkaTopic(msg.getKafkaTopic());
        doc.setKafkaPartition(msg.getKafkaPartition());
        doc.setKafkaOffset(msg.getKafkaOffset());
        doc.setKafkaKey(sanitizeKafkaKey(msg.getKafkaKey()));
        return doc;
    }

    /**
     * 清洗 Kafka Key
     *
     * 实现逻辑：
     * 1. 过滤 null 和空字符串
     * 2. 检查是否包含非可打印字符（二进制数据）
     * 3. 二进制数据转换为 Base64 格式，有效字符串原样返回
     *
     * @param key 原始 Kafka Key
     * @return 清洗后的 Key
     */
    private String sanitizeKafkaKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        // 检查是否包含非可打印 ASCII 字符（控制字符等二进制数据）
        for (char c : key.toCharArray()) {
            if (c < 32 && c != '\t' && c != '\n' && c != '\r') {
                // 包含控制字符，转换为 Base64 格式
                return "base64:" + java.util.Base64.getEncoder().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return key;
    }

    /**
     * 截断消息内容
     *
     * 实现逻辑：
     * 1. 判断长度是否超过限制。
     * 2. 超过则截断并追加省略号。
     *
     * @param message 原始消息
     * @param maxLength 最大长度
     * @return 截断后的消息
     */
    private String truncateMessage(String message, int maxLength) {
        // 实现思路：
        // 1. 判空与长度校验。
        // 2. 超限时截断。
        if (message == null) {
            return null;
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength) + "...";
    }

    /**
     * 判断是否应该重试
     *
     * 实现逻辑：
     * 1. 根据错误信息匹配可重试关键字。
     *
     * @param e 异常对象
     * @return 是否可重试
     */
    private boolean shouldRetry(Exception e) {
        // 实现思路：
        // 1. 从异常信息中匹配网络与超时关键字。
        // 网络异常、超时等可重试
        String errorMessage = e.getMessage();
        if (errorMessage != null) {
            String lowerMessage = errorMessage.toLowerCase();
            return lowerMessage.contains("timeout") || 
                   lowerMessage.contains("connection") ||
                   lowerMessage.contains("network");
        }
        return false;
    }

    /**
     * 日志消息实体类
     *
     * 设计目的：
     * 1. 统一承载日志与Kafka元信息。
     *
     * 为什么需要该类：
     * - 便于在消费链路中传递完整上下文。
     *
     * 核心实现思路：
     * - 使用字段承载日志与元信息，通过 Lombok 自动生成访问器方法。
     */
    @lombok.Data
    public static class LogMessage {
        private String env;
        private String service;
        private String hostname;
        private String ip;
        private String port;
        private String level;
        private String thread;
        private String logger;
        private String message;
        private String exception;
        private LocalDateTime timestamp;
        private String instanceId;
        
        // Kafka 元信息
        private String kafkaTopic;
        private int kafkaPartition;
        private long kafkaOffset;
        private String kafkaKey;
        private LocalDateTime consumeTime;
    }
}
