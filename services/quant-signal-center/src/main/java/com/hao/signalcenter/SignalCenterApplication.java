package com.hao.signalcenter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 信号中心启动类 (Signal Center Application)
 * <p>
 * 模块职责：
 * 作为整个量化交易架构的"核心枢纽"，负责：
 * 1. 消费 Kafka 策略信号
 * 2. 旁路查询风控分数
 * 3. 幂等落库 MySQL
 * 4. 主动推送 Redis 缓存
 * <p>
 * 设计目的：
 * 实现"计算"与"IO"的解耦，策略引擎只管计算发信号，
 * 本模块作为唯一的写入口，保证数据一致性。
 *
 * @author hli
 * @date 2026-01-30
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.hao.signalcenter.mapper")
public class SignalCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalCenterApplication.class, args);
    }
}
