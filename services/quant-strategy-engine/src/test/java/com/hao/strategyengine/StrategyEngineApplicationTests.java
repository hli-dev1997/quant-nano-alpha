package com.hao.strategyengine;

import org.junit.jupiter.api.Test;

/**
 * 启动类测试（极简版）
 * 
 * 注意：完整的 Spring Boot 集成测试需要 Kafka/Redis 环境
 * 这里仅作为编译检查占位符
 */
class StrategyEngineApplicationTests {

    @Test
    void contextLoads() {
        // 极简模式：不启动 Spring 容器
        // 如需完整测试，请配置 test profile 并提供 Kafka/Redis
        System.out.println("极简模式：跳过 Spring Boot 启动测试");
    }
}
