package com.hao.strategyengine;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 启动类测试（极简版）
 * 
 * 注意：完整的 Spring Boot 集成测试需要 Kafka/Redis 环境
 * 这里仅作为编译检查占位符
 */
@Slf4j
class StrategyEngineApplicationTests {

    @Test
    void contextLoads() {
        // 极简模式：不启动 Spring 容器
        // 如需完整测试，请配置 test profile 并提供 Kafka/Redis
        log.info("极简模式_跳过_Spring_Boot_启动测试|Skip_Spring_Boot_startup_test");
    }
}
