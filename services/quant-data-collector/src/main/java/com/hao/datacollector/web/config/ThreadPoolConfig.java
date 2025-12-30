package com.hao.datacollector.web.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置类 - 针对量化交易数据收集系统优化
 *
 * @author hli
 * @program datacollector
 * @date 2025-06-04 19:24:56
 * @description 提供多种类型的线程池配置，满足不同业务场景需求
 * <p>
 * 配置策略说明：
 * 1. IO密集型任务：数据库操作、文件读写、网络请求等
 * 2. CPU密集型任务：数据计算、加密解密、图像处理等
 * 3. 混合型任务：既有IO又有CPU计算的复合任务
 * 4. 虚拟线程：Java 21+的轻量级线程，适合大量并发IO操作
 * <p>
 * 监控指标：
 * - 活跃线程数：executor.getActiveCount()
 * - 队列长度：executor.getThreadPoolExecutor().getQueue().size()
 * - 完成任务数：executor.getThreadPoolExecutor().getCompletedTaskCount()
 * - 拒绝任务数：通过自定义RejectedExecutionHandler统计
 */
@Slf4j // Lombok注解：自动生成日志对象
@RequiredArgsConstructor // Lombok注解：生成包含final字段的构造函数
@EnableAsync // 启用Spring异步执行功能
@Configuration // 标记为Spring配置类
public class ThreadPoolConfig implements AsyncConfigurer {

    // 从配置文件读取队列容量，默认值200
    @Value("${async.executor.queue.capacity:200}")
    private Integer queueCapacity;

    // 从配置文件读取线程存活时间，默认值60秒
    @Value("${async.executor.keep.alive.seconds:60}")
    private Integer keepAliveSeconds;

    // 从配置文件读取核心线程数倍数，默认值4（适用于IO密集型）
    @Value("${async.executor.core.pool.multiplier:4}")
    private Integer corePoolMultiplier;

    // 从配置文件读取最大线程数倍数，默认值8
    @Value("${async.executor.max.pool.multiplier:8}")
    private Integer maxPoolMultiplier;

    /**
     * 获取当前系统CPU核心数
     * 用于动态计算线程池大小
     */
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    // 存储所有线程池实例，用于优雅关闭
    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();

    /**
     * IO密集型线程池配置
     * <p>
     * 适用场景：
     * - 数据库查询和更新操作
     * - 文件读写操作
     * - HTTP/HTTPS网络请求
     * - 消息队列操作
     * <p>
     * 计算公式：CPU核数 × (1 + IO等待时间/CPU处理时间)
     * 假设IO等待时间50ms，CPU处理时间1ms，则：24 × (1 + 50/1) = 1224
     * 实际考虑系统开销和内存限制，设置为CPU核数的4-8倍
     *
     * @return ThreadPoolTaskExecutor IO密集型线程池
     */
    @Bean("ioTaskExecutor")
    public ThreadPoolTaskExecutor ioTaskExecutor() {
        log.info("初始化IO密集型线程池，CPU核数：{}", CPU_CORES);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核数 × 4，保证基础并发能力
        int corePoolSize = CPU_CORES * corePoolMultiplier;
        executor.setCorePoolSize(corePoolSize);
        log.debug("IO线程池核心线程数设置为：{}", corePoolSize);
        // 最大线程数：CPU核数 × 8，处理突发流量
        int maxPoolSize = CPU_CORES * maxPoolMultiplier;
        executor.setMaxPoolSize(maxPoolSize);
        log.debug("IO线程池最大线程数设置为：{}", maxPoolSize);
        // 队列容量：设置较大队列，缓冲突发任务
        executor.setQueueCapacity(queueCapacity * 5); // 1000
        log.debug("IO线程池队列容量设置为：{}", queueCapacity * 5);
        // 线程存活时间：空闲线程超过此时间将被回收
        executor.setKeepAliveSeconds(keepAliveSeconds * 2); // 120秒
        // 线程名称前缀：便于日志追踪和问题定位
        executor.setThreadNamePrefix("io-task-");
        // 允许核心线程超时：在低负载时回收核心线程，节省资源
        executor.setAllowCoreThreadTimeOut(true);
        // 拒绝策略：调用者运行策略，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭配置：等待任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // 初始化线程池
        executor.initialize();
        // 添加到管理列表
        executors.add(executor);
        log.info("IO密集型线程池初始化完成|IO_thread_pool_ready,coreSize={},maxSize={},queueSize={}",
                corePoolSize, maxPoolSize, queueCapacity * 5);
        return executor;
    }

    /**
     * CPU密集型线程池配置
     * <p>
     * 适用场景：
     * - 数学计算和统计分析
     * - 数据加密解密
     * - 图像/视频处理
     * - 算法执行
     * <p>
     * 计算公式：CPU核数 + 1
     * 避免线程数过多导致上下文切换开销
     *
     * @return ThreadPoolTaskExecutor CPU密集型线程池
     */
    @Bean("cpuTaskExecutor")
    public ThreadPoolTaskExecutor cpuTaskExecutor() {
        log.info("初始化CPU密集型线程池");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核数 + 1，充分利用CPU资源
        int corePoolSize = CPU_CORES + 1;
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数：CPU核数 × 2，处理短时间突发
        int maxPoolSize = CPU_CORES * 2;
        executor.setMaxPoolSize(maxPoolSize);
        // 队列容量：较小队列，避免任务堆积影响响应时间
        executor.setQueueCapacity(queueCapacity / 2); // 100
        // 线程存活时间：CPU密集型任务通常执行时间较短
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // 线程名称前缀
        executor.setThreadNamePrefix("cpu-task-");
        // 不允许核心线程超时：保持稳定的计算能力
        executor.setAllowCoreThreadTimeOut(false);
        // 拒绝策略：直接抛异常，快速失败
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅关闭配置
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        executors.add(executor);
        log.info("CPU密集型线程池初始化完成|CPU_thread_pool_ready,coreSize={},maxSize={},queueSize={}",
                corePoolSize, maxPoolSize, queueCapacity / 2);
        return executor;
    }

    /**
     * 混合型线程池配置
     * <p>
     * 适用场景：
     * - 数据处理流水线（IO + 计算）
     * - 报表生成（查询 + 计算 + 输出）
     * - 数据同步任务
     * <p>
     * 平衡IO和CPU需求的中间配置
     *
     * @return ThreadPoolTaskExecutor 混合型线程池
     */
    @Bean("mixedTaskExecutor")
    public ThreadPoolTaskExecutor mixedTaskExecutor() {
        log.info("初始化混合型线程池|Init_mixed_thread_pool");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：CPU核数 × 2，平衡IO和CPU需求
        int corePoolSize = CPU_CORES * 2;
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数：CPU核数 × 4
        int maxPoolSize = CPU_CORES * 4;
        executor.setMaxPoolSize(maxPoolSize);
        // 队列容量：中等大小队列
        executor.setQueueCapacity(queueCapacity * 2); // 400
        // 线程存活时间：适中的存活时间
        executor.setKeepAliveSeconds(keepAliveSeconds + 30); // 90秒
        executor.setThreadNamePrefix("mixed-task-");
        executor.setAllowCoreThreadTimeOut(true);
        // 拒绝策略：调用者运行，保证任务执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        executor.initialize();
        executors.add(executor);
        log.info("混合型线程池初始化完成|Mixed_thread_pool_ready,coreSize={},maxSize={},queueSize={}",
                corePoolSize, maxPoolSize, queueCapacity * 2);
        return executor;
    }

    /**
     * 虚拟线程执行器（Java 21+）
     * <p>
     * 适用场景：
     * - 大量并发IO操作
     * - 微服务间调用
     * - 长连接处理
     * <p>
     * 优势：
     * - 轻量级，创建成本极低
     * - 自动管理，无需手动配置线程数
     * - 适合高并发场景
     *
     * @return Executor 虚拟线程执行器
     */
    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        log.info("初始化虚拟线程执行器（需要Java_21+）");
        try {
            // 创建虚拟线程执行器
            Executor executor = Executors.newVirtualThreadPerTaskExecutor();
            log.info("虚拟线程执行器初始化成功|Virtual_thread_executor_ready");
            return executor;
        } catch (Exception e) {
            log.warn("虚拟线程执行器初始化失败，可能是Java版本不支持：{}", e.getMessage());
            // 降级到普通线程池
            return ioTaskExecutor();
        }
    }

    /**
     * 默认异步执行器
     * 实现AsyncConfigurer接口，为@Async注解提供默认线程池
     *
     * @return Executor 默认执行器
     */
    @Override
    @Primary // 标记为主要的Bean
    public Executor getAsyncExecutor() {
        log.info("配置默认异步执行器为IO密集型线程池");
        return ioTaskExecutor();
    }

    /**
     * 异步异常处理器
     * 处理@Async方法中未捕获的异常
     *
     * @return AsyncUncaughtExceptionHandler 异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            // 记录异常信息
            log.error("异步方法执行异常|Async_method_error,method={},params={},error={}",
                    method.getName(), params, ex.getMessage(), ex);
            // 可以在这里添加告警通知、邮件发送等逻辑
            // 例如：alertService.sendAlert("异步任务执行失败", ex);
        };
    }

    /**
     * 量化交易专用线程池
     * 基于性能测试优化的配置
     * <p>
     * 适用场景：
     * - 行情数据处理
     * - 交易信号计算
     * - 数据库批量操作
     *
     * @return ThreadPoolTaskExecutor 量化交易专用线程池
     */
    @Bean("quantTaskExecutor")
    public ThreadPoolTaskExecutor quantTaskExecutor() {
        log.info("初始化量化交易专用线程池|Init_quant_thread_pool");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 基于实测数据优化：24核CPU最优配置约为102线程
        int corePoolSize = (int) (CPU_CORES * 4.25); // 102 for 24 cores
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数：避免性能断崖，设置为核心线程数的1.2倍
        int maxPoolSize = (int) (corePoolSize * 1.2); // 120 for 24 cores
        executor.setMaxPoolSize(maxPoolSize);
        // 队列容量：适中大小，平衡内存使用和任务缓冲
        executor.setQueueCapacity(queueCapacity);
        // 较长的存活时间：量化任务可能有周期性特征
        executor.setKeepAliveSeconds(keepAliveSeconds * 2);
        executor.setThreadNamePrefix("quant-task-");
        // 允许核心线程超时：在非交易时间节省资源
        executor.setAllowCoreThreadTimeOut(true);
        // 拒绝策略：调用者运行，确保重要任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅关闭：等待任务完成，避免数据不一致
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180); // 3分钟等待时间
        executor.initialize();
        executors.add(executor);
        log.info("量化交易专用线程池初始化完成|Quant_thread_pool_ready,coreSize={},maxSize={},queueSize={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    /**
     * 应用关闭时的清理方法
     * 确保所有线程池优雅关闭
     */
    @PreDestroy
    public void destroy() {
        log.info("开始关闭所有线程池|Start_shutdown_all_pools,count={}", executors.size());
        for (ThreadPoolTaskExecutor executor : executors) {
            try {
                String threadNamePrefix = executor.getThreadNamePrefix();
                log.info("正在关闭线程池|Shutdown_pool_start,name={}", threadNamePrefix);
                // 停止接收新任务
                executor.shutdown();
                // 等待现有任务完成
                if (!executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池关闭超时_强制关闭|Pool_shutdown_timeout_force,name={}", threadNamePrefix);
                    executor.getThreadPoolExecutor().shutdownNow();
                }
                log.info("线程池已关闭|Pool_shutdown_success,name={}", threadNamePrefix);
            } catch (Exception e) {
                log.error("关闭线程池异常|Pool_shutdown_error,error={}", e.getMessage(), e);
            }
        }
        log.info("所有线程池关闭完成|All_pools_shutdown_completed");
    }

    /**
     * 获取线程池监控信息
     * 用于健康检查和性能监控
     *
     * @param executor 线程池执行器
     * @return String 监控信息
     */
    public String getExecutorInfo(ThreadPoolTaskExecutor executor) {
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();
        return String.format(
                "线程池[%s] - 核心线程数:%d, 最大线程数:%d, 当前线程数:%d, 活跃线程数:%d, " +
                        "队列大小:%d, 已完成任务数:%d, 总任务数:%d",
                executor.getThreadNamePrefix(),
                threadPool.getCorePoolSize(),
                threadPool.getMaximumPoolSize(),
                threadPool.getPoolSize(),
                threadPool.getActiveCount(),
                threadPool.getQueue().size(),
                threadPool.getCompletedTaskCount(),
                threadPool.getTaskCount()
        );
    }
}
