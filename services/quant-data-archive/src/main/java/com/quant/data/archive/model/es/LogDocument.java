package com.quant.data.archive.model.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch 日志文档实体类
 *
 * 设计目的：
 * 1. 映射日志字段到 ES 文档结构
 * 2. 支持 Kibana 日志检索和分析
 *
 * 为什么需要该类：
 * - 与业务 LogMessage 解耦，专注 ES 存储需求
 * - 通过注解定义 ES 字段类型和索引策略
 *
 * 字段类型说明：
 * - Keyword：精确匹配（服务名、级别、IP 等）
 * - Text：全文搜索（日志内容、异常堆栈）
 * - Date：时间戳
 * - Integer/Long：数值字段
 */
@Document(indexName = "quant-logs")
public class LogDocument {

    /** ES 文档 ID（自动生成） */
    @Id
    private String id;

    /** 服务名 */
    @Field(type = FieldType.Keyword)
    private String service;

    /** 环境（dev/test/prod） */
    @Field(type = FieldType.Keyword)
    private String env;

    /** 日志级别（DEBUG/INFO/WARN/ERROR） */
    @Field(type = FieldType.Keyword)
    private String level;

    /** 日志内容（支持全文搜索） */
    @Field(type = FieldType.Text)
    private String message;

    /** 异常堆栈（支持全文搜索） */
    @Field(type = FieldType.Text)
    private String exception;

    /** 线程名 */
    @Field(type = FieldType.Keyword)
    private String thread;

    /** Logger 名 */
    @Field(type = FieldType.Keyword)
    private String logger;

    /** 主机名 */
    @Field(type = FieldType.Keyword)
    private String hostname;

    /** IP 地址 */
    @Field(type = FieldType.Keyword)
    private String ip;

    /** 端口 */
    @Field(type = FieldType.Keyword)
    private String port;

    /** 实例标识（service-ip-port） */
    @Field(type = FieldType.Keyword)
    private String instanceId;

    /** 日志时间戳 */
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    /** 消费时间 */
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime consumeTime;

    /** Kafka Topic */
    @Field(type = FieldType.Keyword)
    private String kafkaTopic;

    /** Kafka 分区 */
    @Field(type = FieldType.Integer)
    private Integer kafkaPartition;

    /** Kafka 偏移量 */
    @Field(type = FieldType.Long)
    private Long kafkaOffset;

    /** Kafka Key */
    @Field(type = FieldType.Keyword)
    private String kafkaKey;

    // ==================== Getter/Setter ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public LocalDateTime getConsumeTime() {
        return consumeTime;
    }

    public void setConsumeTime(LocalDateTime consumeTime) {
        this.consumeTime = consumeTime;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public Integer getKafkaPartition() {
        return kafkaPartition;
    }

    public void setKafkaPartition(Integer kafkaPartition) {
        this.kafkaPartition = kafkaPartition;
    }

    public Long getKafkaOffset() {
        return kafkaOffset;
    }

    public void setKafkaOffset(Long kafkaOffset) {
        this.kafkaOffset = kafkaOffset;
    }

    public String getKafkaKey() {
        return kafkaKey;
    }

    public void setKafkaKey(String kafkaKey) {
        this.kafkaKey = kafkaKey;
    }

    @Override
    public String toString() {
        return "LogDocument{" +
                "id='" + id + '\'' +
                ", service='" + service + '\'' +
                ", level='" + level + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
