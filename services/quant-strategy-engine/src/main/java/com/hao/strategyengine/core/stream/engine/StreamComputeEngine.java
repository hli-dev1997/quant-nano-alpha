package com.hao.strategyengine.core.stream.engine;

import com.hao.strategyengine.config.StreamComputeProperties;
import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;
import com.hao.strategyengine.core.stream.strategy.StreamingStrategy;
import com.hao.strategyengine.integration.redis.RedisStrategyRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 流式计算核心引擎
 *
 * 设计目的：
 * 1. 管理分区Worker线程池，实现Thread Confinement无锁并发。
 * 2. 维护股票→Context的内存映射，支持高效状态访问。
 * 3. 协调Tick处理流程：路由→更新状态→策略计算→结果存储。
 *
 * 核心设计思想：
 * ┌─────────────────────────────────────────────────────────────┐
 * │  Thread Confinement (线程封闭)                              │
 * │                                                             │
 * │  同一股票的所有Tick永远由同一个Worker线程处理：              │
 * │  - Hash(symbol) % N 决定分配给哪个Worker                    │
 * │  - 每个Worker独立维护自己负责的股票Context                  │
 * │  - 无需任何锁机制，天然线程安全                              │
 * │                                                             │
 * │  类比：Kafka分区消费模型                                     │
 * │  - 一个Partition只被一个Consumer消费                         │
 * │  - 一只股票只被一个Worker处理                                │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 并发安全保证：
 * 1. contextMaps数组：每个Worker独占自己的Map，无竞争
 * 2. strategies列表：只读，初始化后不变
 * 3. 单个Context：由固定Worker独占访问，无需synchronized
 *
 * 异常处理策略：
 * - 单个策略异常不影响其他策略执行
 * - 单只股票异常不影响其他股票处理
 * - Worker线程永不崩溃，保证系统稳定
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Component
public class StreamComputeEngine {

    /**
     * Worker线程池数组
     * 每个元素是一个单线程执行器，负责处理分配给它的所有股票
     */
    private ExecutorService[] workers;

    /**
     * Context分区映射数组
     * 每个Worker独占一个Map，避免ConcurrentHashMap的性能开销
     * 设计原因：虽然初始化时使用ConcurrentHashMap保证首次put的原子性，
     * 但后续操作由Worker线程独占，无需并发控制
     */
    private Map<String, StockDomainContext>[] contextMaps;

    /**
     * 流式策略列表
     * 通过Spring自动注入所有StreamingStrategy实现
     */
    private final List<StreamingStrategy> strategies;

    /**
     * Redis存储仓库
     */
    private final RedisStrategyRepository repository;

    /**
     * 配置属性
     */
    private final StreamComputeProperties properties;

    /**
     * Worker数量
     */
    private int workerCount;

    /**
     * 构造器注入依赖
     */
    public StreamComputeEngine(List<StreamingStrategy> strategies,
                               RedisStrategyRepository repository,
                               StreamComputeProperties properties) {
        this.strategies = strategies;
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 初始化引擎
     *
     * 实现逻辑：
     * 1. 根据配置创建Worker线程池数组
     * 2. 初始化每个Worker的Context映射
     * 3. 记录启动日志
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void init() {
        if (!properties.isEnabled()) {
            log.info("流式计算引擎已禁用|Stream_compute_engine_disabled");
            return;
        }

        this.workerCount = properties.getWorkerThreads();

        // 中文：创建Worker线程池数组，每个Worker是单线程执行器
        // English: Create worker thread pool array, each worker is single-thread executor
        this.workers = new ExecutorService[workerCount];
        this.contextMaps = new ConcurrentHashMap[workerCount];

        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            // 中文：使用newSingleThreadExecutor保证任务顺序执行
            // English: Use newSingleThreadExecutor to ensure sequential task execution
            workers[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stream-worker-" + workerId);
                // 中文：设置为非守护线程，确保优雅关闭时任务可完成
                // English: Set as non-daemon to ensure tasks complete on graceful shutdown
                t.setDaemon(false);
                return t;
            });
            // 中文：使用HashMap替代ConcurrentHashMap，因为单线程访问无需并发控制
            // English: Use HashMap instead of ConcurrentHashMap, single-thread access needs no concurrency control
            contextMaps[i] = new ConcurrentHashMap<>();
        }

        log.info("流式计算引擎初始化完成|Stream_compute_engine_initialized,workerCount={},strategies={}",
                workerCount, strategies.size());
    }

    /**
     * 处理Tick数据（入口方法）
     *
     * 实现逻辑：
     * 1. 计算路由槽位：Hash(symbol) % workerCount
     * 2. 将任务提交给对应Worker异步执行
     *
     * 为什么使用Math.abs：
     * - hashCode可能为负数，取模后仍为负数
     * - abs保证结果为非负数，正确映射到数组索引
     *
     * @param tick 实时行情数据
     */
    public void process(Tick tick) {
        if (!properties.isEnabled() || tick == null) {
            return;
        }

        // 中文：计算路由槽位，保证同一股票永远分配给同一Worker
        // English: Calculate routing slot, ensure same stock always goes to same worker
        int slot = Math.abs(tick.getSymbol().hashCode()) % workerCount;

        // 中文：异步提交给目标Worker处理
        // English: Async submit to target worker for processing
        workers[slot].submit(() -> doProcess(tick, slot));
    }

    /**
     * 核心处理逻辑（由Worker线程执行）
     *
     * 实现逻辑：
     * 1. 获取或创建股票Context
     * 2. 更新Context状态（写入最新价格）
     * 3. 遍历所有策略执行计算
     * 4. 命中策略则写入Redis
     *
     * 异常隔离设计：
     * - 每个策略的执行都包裹在try-catch中
     * - 单个策略异常不影响其他策略
     * - 保证Worker线程永不崩溃
     *
     * @param tick 实时行情数据
     * @param slot 路由槽位（Worker索引）
     */
    private void doProcess(Tick tick, int slot) {
        try {
            // 中文：从分区Map中获取Context，不存在则创建
            // English: Get context from partition map, create if not exists
            StockDomainContext context = contextMaps[slot].computeIfAbsent(
                    tick.getSymbol(),
                    symbol -> {
                        log.debug("创建新的股票上下文|Create_new_stock_context,symbol={},slot={}",
                                symbol, slot);
                        // TODO: 此处可预留历史数据预热逻辑，从DB加载历史价格填充Context
                        return new StockDomainContext(symbol, properties.getHistorySize());
                    }
            );

            // 中文：更新Context状态，写入最新价格到RingBuffer
            // English: Update context state, write latest price to RingBuffer
            context.update(tick.getPrice());

            // 中文：遍历所有策略进行计算
            // English: Iterate all strategies for computation
            for (StreamingStrategy strategy : strategies) {
                try {
                    // 中文：执行策略判断，纯内存计算
                    // English: Execute strategy check, pure memory computation
                    boolean matched = strategy.isMatch(tick, context);

                    if (matched) {
                        // 中文：命中策略，异步写入Redis
                        // English: Strategy matched, async write to Redis
                        repository.saveMatch(strategy.getId(), tick.getSymbol());
                    }
                } catch (Exception e) {
                    // 中文：单个策略异常隔离，不影响其他策略执行
                    // English: Isolate single strategy exception, don't affect other strategies
                    log.error("策略执行异常|Strategy_execution_error,strategyId={},symbol={}",
                            strategy.getId(), tick.getSymbol(), e);
                }
            }
        } catch (Exception e) {
            // 中文：顶层兜底，保证Worker线程永不崩溃
            // English: Top-level catch, ensure worker thread never crashes
            log.error("Tick处理异常|Tick_processing_error,symbol={}", tick.getSymbol(), e);
        }
    }

    /**
     * 优雅关闭引擎
     *
     * 实现逻辑：
     * 1. 先shutdown停止接收新任务
     * 2. 等待现有任务完成（最多30秒）
     * 3. 超时则强制关闭
     */
    @PreDestroy
    public void shutdown() {
        if (workers == null) {
            return;
        }

        log.info("开始关闭流式计算引擎|Stream_compute_engine_shutting_down");

        for (int i = 0; i < workers.length; i++) {
            ExecutorService worker = workers[i];
            if (worker != null) {
                worker.shutdown();
                try {
                    // 中文：等待任务完成，最多30秒
                    // English: Wait for tasks to complete, max 30 seconds
                    if (!worker.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("Worker关闭超时_强制终止|Worker_shutdown_timeout_force,workerId={}", i);
                        worker.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    worker.shutdownNow();
                }
            }
        }

        log.info("流式计算引擎已关闭|Stream_compute_engine_shutdown_complete");
    }

    /**
     * 获取指定股票的Context（仅供测试和监控使用）
     *
     * @param symbol 股票代码
     * @return Context对象，不存在返回null
     */
    public StockDomainContext getContext(String symbol) {
        int slot = Math.abs(symbol.hashCode()) % workerCount;
        return contextMaps[slot].get(symbol);
    }

    /**
     * 获取Context总数（监控指标）
     *
     * @return 所有分区的Context总数
     */
    public int getContextCount() {
        int total = 0;
        for (Map<String, StockDomainContext> map : contextMaps) {
            if (map != null) {
                total += map.size();
            }
        }
        return total;
    }
}
