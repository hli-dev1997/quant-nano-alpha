package com.hao.strategyengine.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 策略引擎线程池配置
 *
 * 设计目的：
 * 1. 为策略并行执行提供专用线程池
 * 2. 避免策略执行阻塞 Kafka 消费线程
 *
 * @author hli
 * @date 2026-01-21
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    @Value("${strategy.executor.queue.capacity:500}")
    private Integer queueCapacity;

    @Value("${strategy.executor.keep.alive.seconds:60}")
    private Integer keepAliveSeconds;

    private ThreadPoolTaskExecutor strategyExecutor;

    /**
     * 策略执行线程池
     * <p>
     * 策略判断主要是 CPU 密集型操作（公式计算），线程数设为 CPU 核数 × 2。
     * 队列容量设置较大，用于缓冲高峰期的行情数据。
     *
     * @return ThreadPoolTaskExecutor 策略执行专用线程池
     */
    @Bean("strategyExecutor")
    public ThreadPoolTaskExecutor strategyExecutor() {
        log.info("初始化策略执行线程池|Init_strategy_thread_pool,cpuCores={}", CPU_CORES);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：CPU 核数 × 2
        int corePoolSize = CPU_CORES * 2;
        executor.setCorePoolSize(corePoolSize);
        
        // 最大线程数：CPU 核数 × 4
        int maxPoolSize = CPU_CORES * 4;
        executor.setMaxPoolSize(maxPoolSize);
        
        // 队列容量
        executor.setQueueCapacity(queueCapacity);
        
        // 线程存活时间
        executor.setKeepAliveSeconds(keepAliveSeconds);
        
        // 线程名称前缀：便于日志追踪
        executor.setThreadNamePrefix("strategy-");
        
        // 允许核心线程超时：低负载时回收线程
        executor.setAllowCoreThreadTimeOut(true);
        
        // 拒绝策略：调用者运行，保证不丢失行情数据
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        this.strategyExecutor = executor;
        
        log.info("策略执行线程池初始化完成|Strategy_thread_pool_ready,coreSize={},maxSize={},queueSize={}",
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }

    /**
     * 应用关闭时清理线程池
     */
    @PreDestroy
    public void destroy() {
        if (strategyExecutor != null) {
            log.info("开始关闭策略执行线程池|Shutdown_strategy_pool");
            try {
                strategyExecutor.shutdown();
                if (!strategyExecutor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("策略线程池关闭超时_强制关闭|Strategy_pool_timeout_force_shutdown");
                    strategyExecutor.getThreadPoolExecutor().shutdownNow();
                }
                log.info("策略执行线程池已关闭|Strategy_pool_shutdown_success");
            } catch (Exception e) {
                log.error("策略线程池关闭异常|Strategy_pool_shutdown_error", e);
            }
        }
    }
}
