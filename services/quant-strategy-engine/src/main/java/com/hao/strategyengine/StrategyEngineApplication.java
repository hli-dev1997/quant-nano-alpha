package com.hao.strategyengine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;

/**
 * 策略引擎服务启动类（极简版）
 *
 * 设计目的：
 * 1. 启动流式计算引擎服务。
 * 2. 自动装配Kafka消费者和Redis连接。
 *
 * 为什么需要该类：
 * - 作为Spring Boot服务入口，承载启动流程。
 *
 * 核心实现思路：
 * - Kafka消费者自动启动并开始消费消息
 * - StreamComputeEngine通过@PostConstruct初始化Worker线程池
 * - 整个流程由Spring容器自动管理生命周期
 *
 * 极简架构说明：
 * - 不再依赖数据库（排除DataSource自动配置）
 * - 不再依赖MyBatis（无Mapper扫描）
 * - 仅保留Kafka和Redis核心依赖
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@SpringBootApplication(exclude = {
        // 中文：排除数据源自动配置，极简模式不需要MySQL
        // English: Exclude DataSource auto-config, bare metal mode doesn't need MySQL
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
})
public class StrategyEngineApplication {

    /**
     * 启动入口
     *
     * 实现逻辑：
     * 1. 启动Spring应用上下文。
     * 2. 自动装配并启动：
     *    - KafkaConsumerService（消费行情消息）
     *    - StreamDispatchEngine（消息解析分发）
     *    - StreamComputeEngine（Worker线程池）
     *    - RedisStrategyRepository（结果存储）
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 实现思路：
        // 1. 交由SpringApplication启动上下文。
        // 2. 所有组件通过@Component自动注入。
        SpringApplication.run(StrategyEngineApplication.class, args);
        log.info("策略引擎服务启动完成|Strategy_engine_service_started");
    }
}
