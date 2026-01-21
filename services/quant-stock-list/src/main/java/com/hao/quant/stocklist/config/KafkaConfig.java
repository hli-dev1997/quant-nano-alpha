package com.hao.quant.stocklist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 类说明 / Class Description:
 * 中文：Kafka 消息队列配置类。
 * English: Kafka message queue configuration class.
 *
 * 设计目的 / Design Purpose:
 * 中文：启用 Kafka 功能并提供基础配置，待功能完善后添加消费者监听器。
 * English: Enable Kafka and provide basic configuration; consumers to be added as features develop.
 *
 * 核心实现思路 / Implementation:
 * 中文：通过 @EnableKafka 启用 Spring Kafka 自动配置，具体消费者配置待后续添加。
 * English: Enable Spring Kafka auto-configuration via @EnableKafka; specific consumer config to be added later.
 */
@EnableKafka
@Configuration
public class KafkaConfig {
    // TODO: 待其他模块产出数据后添加消费者配置
}
