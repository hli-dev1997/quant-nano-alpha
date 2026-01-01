package com.hao.strategyengine.integration.kafka;

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
 * Kafka消费者配置类
 *
 * <p>设计目的：
 * 1. 统一配置策略引擎服务的Kafka消费者参数。
 * 2. 提供手动提交模式的消费者工厂，确保消息可靠消费。
 *
 * <p>为什么需要该类：
 * - Spring Boot自动配置的默认消费者不满足手动提交需求。
 * - 需要自定义消费组、反序列化器等参数。
 *
 * <p>核心实现思路：
 * - 关闭自动提交(enable.auto.commit=false)。
 * - 使用MANUAL_IMMEDIATE模式，由业务代码控制提交时机。
 * - 消费组ID使用strategy-service-group标识策略服务。
 *
 * @author quant-team
 * @since 2025-10-22
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * 创建Kafka消费者工厂
     *
     * <p>实现逻辑：
     * 1. 配置Kafka集群地址。
     * 2. 设置消费组ID。
     * 3. 配置Key/Value反序列化器。
     * 4. 关闭自动提交，由业务代码手动控制。
     *
     * @return 消费者工厂实例
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "strategy-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // 关闭自动提交，确保消息处理成功后再提交偏移量
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 创建Kafka监听器容器工厂
     *
     * <p>实现逻辑：
     * 1. 使用自定义的消费者工厂。
     * 2. 设置手动立即提交模式(MANUAL_IMMEDIATE)。
     *
     * @return 监听器容器工厂实例
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 手动立即提交模式：调用ack.acknowledge()后立即提交偏移量
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
