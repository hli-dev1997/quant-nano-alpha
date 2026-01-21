package com.hao.datacollector;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;

import java.util.concurrent.Executor;

/**
 * 数据采集服务启动类
 *
 * 设计目的：
 * 1. 启动数据采集服务并加载Spring上下文。
 * 2. 注册Nacos配置监听，感知配置变更。
 *
 * 为什么需要该类：
 * - 作为应用入口统一承载启动与配置治理流程。
 *
 * 核心实现思路：
 * - 通过ApplicationRunner注册配置监听器并记录变更日志。
 */
@Slf4j
@MapperScan("com.hao.datacollector.dal.dao")
@SpringBootApplication
@EnableDiscoveryClient//开启服务发现功能
@EnableCaching//缓存
@EnableRetry
public class DataCollectorApplication {

    /**
     * 启动入口
     *
     * 实现逻辑：
     * 1. 启动Spring应用上下文。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 实现思路：
        // 1. 交由SpringApplication启动上下文。
        SpringApplication.run(DataCollectorApplication.class, args);
    }

    // 实现思路：
    // 1. 项目启动后监听配置文件变化。
    // 2. 发生变化后记录日志并预留通知入口。
    @Bean
    ApplicationRunner applicationRunner(NacosConfigManager nacosConfigManager) {
        /**
         * 注册配置监听器
         *
         * 实现逻辑：
         * 1. 获取Nacos配置服务并注册监听器。
         * 2. 输出配置变更日志，便于运维排查。
         *
         * @param nacosConfigManager Nacos配置管理器
         * @return ApplicationRunner回调
         */
        return args -> {
            ConfigService configService = nacosConfigManager.getConfigService();
            configService.addListener("application-dev.yml", "quant-data-collector", new Listener() {
                @Override
                public Executor getExecutor() {
                    // 实现思路：
                    // 1. 使用ThreadPoolExecutor替代Executors（阿里巴巴规范）
                    return new java.util.concurrent.ThreadPoolExecutor(
                            2, 2, 60L, java.util.concurrent.TimeUnit.SECONDS,
                            new java.util.concurrent.LinkedBlockingQueue<>(100),
                            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
                    );
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    // 实现思路：
                    // 1. 输出配置变更内容。
                    // 2. 预留告警通知入口。
                    log.info("配置监听变更|Config_listener_change,configInfo={}", configInfo);
                    log.info("发送告警邮件|Send_alert_email");
                }
            });
            log.info("启动配置监听|Start_config_listener");
        };
    }
}
