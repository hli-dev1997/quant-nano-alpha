package com.hao.quant.stocklist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 类说明 / Class Description:
 * 中文：Redis 基础配置类。
 * English: Redis basic configuration class.
 *
 * 设计目的 / Design Purpose:
 * 中文：统一封装 Redis 模板配置，提供标准化的序列化方式，避免各处零散配置。
 * English: Centralize Redis template configuration with standardized serialization to avoid scattered configs.
 *
 * 核心实现思路 / Implementation:
 * 中文：使用 StringRedisSerializer 处理 Key，GenericJackson2JsonRedisSerializer 处理 Value，支持对象存储。
 * English: Use StringRedisSerializer for keys, GenericJackson2JsonRedisSerializer for values to support object storage.
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate。
     *
     * 实现逻辑：
     * 1. 创建 RedisTemplate 并绑定连接工厂。
     * 2. Key 使用字符串序列化器，保证可读性。
     * 3. Value 使用 JSON 序列化器，支持复杂对象存储。
     * 4. Hash 的 Key/Value 采用相同策略。
     *
     * @param connectionFactory Redis 连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // 绑定连接工厂
        template.setConnectionFactory(connectionFactory);
        // Key 使用字符串序列化，保证可读性
        template.setKeySerializer(new StringRedisSerializer());
        // Value 使用 JSON 序列化，支持对象存储
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        // Hash Key/Value 采用相同策略
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
