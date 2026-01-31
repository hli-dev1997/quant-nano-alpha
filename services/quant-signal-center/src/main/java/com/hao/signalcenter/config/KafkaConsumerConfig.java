package com.hao.signalcenter.config;

import integration.kafka.KafkaConstants;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 消费者配置类 (Kafka Consumer Configuration)
 * <p>
 * 类职责：
 * 配置信号中心的 Kafka 消费者，确保消息不丢失、不重复处理。
 * <p>
 * 核心配置（保证消息可靠性）：
 * 1. enable.auto.commit=false：禁用自动提交，手动确认 Offset
 * 2. ack-mode=MANUAL_IMMEDIATE：处理完成后立即手动确认
 * 3. max.poll.records=100：每次拉取最多 100 条，避免处理超时
 * <p>
 * 设计目的：
 * 确保 Kafka 消息在 MySQL 落库和 Redis 更新完成后才确认，
 * 避免消息丢失或重复消费导致的数据不一致。
 *
 * @author hli
 * @date 2026-01-30
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:" + KafkaConstants.GROUP_SIGNAL_CENTER + "}")
    private String groupId;

    /**
     * 消费者工厂
     * <p>
     * 配置手动提交 Offset，确保消息处理完成后才确认。
     *
     * @return ConsumerFactory 实例
     */
    @Bean
    public ConsumerFactory<String, String> signalConsumerFactory() {
        Map<String, Object> props = new HashMap<>(16);

        // 基础配置
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // ==================== 可靠性配置 ====================

        // 禁用自动提交 Offset
        // 必须在处理完成后手动调用 ack.acknowledge()
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // 每次拉取最多 100 条消息
        // 避免单次处理时间过长导致会话超时
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // 两次 poll 之间的最大间隔时间（默认 5 分钟）
        // 超过此时间未 poll，消费者会被踢出消费组
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // 会话超时时间（默认 45 秒）
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45000);

        // 从最早的消息开始消费（首次启动或 Offset 丢失时）
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka 监听器容器工厂
     * <p>
     * 配置手动确认模式，确保消息处理完成后才确认 Offset。
     *
     * @param signalConsumerFactory 消费者工厂
     * @return ConcurrentKafkaListenerContainerFactory 实例
     */
    @Bean(name = KafkaConstants.LISTENER_CONTAINER_FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> signalConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(signalConsumerFactory);

        // 手动确认模式：MANUAL_IMMEDIATE
        // 调用 ack.acknowledge() 后立即提交 Offset
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 并发消费者数量（可根据分区数调整）
        factory.setConcurrency(1);

        return factory;
    }
}
