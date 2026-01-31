package com.hao.strategyengine.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 生产者配置类 (Kafka Producer Configuration)
 * <p>
 * 类职责：
 * 配置高可靠性的 Kafka 生产者，用于策略信号的发送。
 * <p>
 * 核心配置（保证消息不丢失）：
 * 1. acks=all：等待所有副本确认消息写入成功
 * 2. retries=MAX_VALUE：无限重试，确保消息最终发送成功
 * 3. enable.idempotence=true：开启幂等性，防止网络抖动导致重复消息
 * 4. max.in.flight.requests.per.connection=5：开启幂等时最多5个未确认请求
 * <p>
 * 设计目的：
 * 在金融系统中，策略信号是核心资产，绝不允许丢失。
 * 通过以上配置，可以保证在极端情况下（如 Broker 宕机）消息依然能够最终送达。
 *
 * @author hli
 * @date 2026-01-30
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * 策略信号生产者工厂
     * <p>
     * 配置高可靠性参数，确保策略信号不丢失：
     * - acks=all：等待 ISR（同步副本）列表中所有 Follower 都写入成功
     * - retries=MAX_VALUE：发送失败时一直重试，直到成功
     * - enable.idempotence=true：开启幂等，保证重试也不会产生重复消息
     *
     * @return ProducerFactory 实例
     */
    @Bean
    public ProducerFactory<String, String> signalProducerFactory() {
        Map<String, Object> props = new HashMap<>(16);

        // 基础配置
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // ==================== 高可靠性配置（防止消息丢失） ====================

        // ACK 模式：all(-1) 表示等待所有副本确认
        // 这是最高可靠性配置，只有当消息被写入 Leader 和所有 ISR 副本后，才认为发送成功
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // 重试次数：设置为最大值，确保消息最终能发送成功
        // 配合幂等性，重试不会产生重复消息
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

        // 开启幂等性：保证单个生产者在单个分区上不会产生重复消息
        // 即使因为网络问题重试，Kafka Broker 也会自动去重
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // 幂等模式下，max.in.flight.requests 最多为 5
        // 超过 5 会报错，因为幂等性需要保证消息顺序
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // ==================== 性能优化配置 ====================

        // 批量发送大小：16KB
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // 发送等待时间：最多等待 5ms 收集批量消息
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // 发送缓冲区大小：32MB
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // 请求超时时间：30 秒
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        // 发送超时时间：2 分钟（包含重试时间）
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * 策略信号 KafkaTemplate
     * <p>
     * 使用高可靠性的 ProducerFactory，用于发送策略信号到 Kafka。
     *
     * @param signalProducerFactory 生产者工厂
     * @return KafkaTemplate 实例
     */
    @Bean
    public KafkaTemplate<String, String> signalKafkaTemplate(ProducerFactory<String, String> signalProducerFactory) {
        return new KafkaTemplate<>(signalProducerFactory);
    }
}
