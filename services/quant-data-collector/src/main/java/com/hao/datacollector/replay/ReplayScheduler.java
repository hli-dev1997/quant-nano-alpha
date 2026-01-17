package com.hao.datacollector.replay;

import com.hao.datacollector.dto.replay.ReplayParamsDTO;
import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.KafkaProducerService;
import constants.DateTimeFormatConstants;
import integration.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 行情回放调度器
 * <p>
 * 核心组件：维护虚拟时钟，按节奏从缓冲区取出数据推送到 Kafka。
 * 支持速度调节和暂停/恢复控制。
 * <p>
 * <b>核心执行链路：</b>
 * <ol>
 *     <li><b>启动 (Start)</b>: 异步启动回放任务，初始化虚拟时间。</li>
 *     <li><b>预加载 (Preload)</b>: {@link DataLoader} 批量从数据库加载历史行情到 {@link TimeSliceBuffer}。</li>
 *     <li><b>调度 (Schedule)</b>: 主循环按秒推进虚拟时间，从缓冲区获取当前秒的数据。</li>
 *     <li><b>推送 (Push)</b>: 将数据批量发送到 Kafka (Topic: quotation)。</li>
 *     <li><b>控制 (Control)</b>: 支持暂停 (Pause)、恢复 (Resume)、调速 (Speed Control)。</li>
 * </ol>
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@Component
public class ReplayScheduler {

    /** 数据加载器，负责从数据库读取历史行情数据 */
    private final DataLoader dataLoader;

    /** 时间片缓冲区，用于缓存预加载的行情数据 */
    private final TimeSliceBuffer buffer;

    /** Kafka 生产者服务，用于高性能推送行情数据 */
    private final KafkaProducerService kafkaProducer;

    /** 回放配置属性（全局默认配置） */
    private final ReplayProperties config;

    /** 当前运行的回放参数（线程安全，每次启动时复制） */
    private volatile ReplayParamsDTO currentParams;

    /**
     * 北京时区偏移量
     */
    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");

    /**
     * 紧凑型日期格式化器 (yyyyMMdd)
     */
    private static final DateTimeFormatter COMPACT_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    /**
     * 虚拟时间（模拟的当前时间，秒级时间戳）
     */
    private volatile long virtualTime;

    /**
     * 预加载游标，记录当前已加载到的时间点
     */
    private volatile LocalDateTime preloadCursor;

    /**
     * 运行状态标志，true表示正在运行
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 暂停状态标志，true表示暂停中
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * 回放统计：已推送的消息总数
     */
    private volatile long totalSentCount = 0;

    /**
     * 显式构造函数，确保依赖注入成功
     *
     * @param dataLoader    数据加载器
     * @param buffer        时间片缓冲区
     * @param kafkaProducer Kafka生产者
     * @param config        回放配置
     */
    public ReplayScheduler(DataLoader dataLoader,
                           TimeSliceBuffer buffer,
                           KafkaProducerService kafkaProducer,
                           ReplayProperties config) {
        Assert.notNull(dataLoader, "DataLoader must not be null");
        Assert.notNull(buffer, "TimeSliceBuffer must not be null");
        Assert.notNull(kafkaProducer, "KafkaProducerService must not be null");
        Assert.notNull(config, "ReplayProperties must not be null");

        this.dataLoader = dataLoader;
        this.buffer = buffer;
        this.kafkaProducer = kafkaProducer;
        this.config = config;

        log.info("ReplayScheduler initialized with config: {}", config);
    }

    /**
     * 使用默认配置启动回放（异步执行）
     * <p>
     * 检查配置是否启用，并确保同一时间只有一个回放任务在运行。
     */
    @Async("ioTaskExecutor")
    public void startReplay() {
        // 构造 DTO 并调用重载方法
        ReplayParamsDTO params = ReplayParamsDTO.builder()
                .startDate(config.getStartDate())
                .endDate(config.getEndDate())
                .speedMultiplier(config.getSpeedMultiplier())
                .preloadMinutes(config.getPreloadMinutes())
                .bufferMaxSize(config.getBufferMaxSize())
                .stockCodes(config.getStockCodes())
                .build();

        if (!config.isEnabled()) {
            log.info("回放模式未启用（全局配置 disabled）|Replay_mode_disabled");
            // 注意：这里我们依然允许调用 startReplay(params) 强制启动，
            // 但如果仅仅是调用 startReplay() 且 config.enabled=false，则拒绝
             return;
        }

        startReplay(params);
    }

    /**
     * 使用指定参数启动回放（异步执行）
     * <p>
     * 即使全局配置 enabled=false，显式调用此方法也会启动回放。
     * 线程安全：使用传入的 params 对象作为本次回放的配置，不受全局配置修改影响。
     *
     * @param params 回放参数 DTO
     */
    @Async("ioTaskExecutor")
    public void startReplay(ReplayParamsDTO params) {
        if (params == null) {
            log.error("启动失败：参数不能为 null");
            return;
        }

        if (running.compareAndSet(false, true)) {
            try {
                // 锁定当前参数
                this.currentParams = params;
                doReplay();
            } catch (Exception e) {
                log.error("回放过程中发生未捕获异常|Replay_exception", e);
            } finally {
                running.set(false);
                this.currentParams = null; // 清理引用
                log.info("回放任务结束，状态已重置|Replay_task_ended");
            }
        } else {
            log.warn("回放已在运行中|Replay_already_running");
        }
    }

    /**
     * 执行回放主流程
     * <p>
     * 1. 初始化虚拟时间为回放开始日期的 09:24:00。<br>
     * 2. 启动异步预加载线程 {@link #startPreloader(LocalDateTime)}。<br>
     * 3. 进入主循环，按秒推进虚拟时间。<br>
     * 4. 每秒从缓冲区取出数据并推送到 Kafka。<br>
     * 5. 处理暂停、休眠和非交易时间跳过。
     */
    private void doReplay() {
        log.info("=== 回放服务启动|Replay_service_start ===");
        log.info("回放参数|Params,startDate={},endDate={},speed={}x,preloadMinutes={}",
                currentParams.getStartDate(), currentParams.getEndDate(),
                currentParams.getSpeedMultiplier(), currentParams.getPreloadMinutes());

        // 初始化时间参数
        LocalDate startDate = LocalDate.parse(currentParams.getStartDate(), COMPACT_FORMATTER);
        LocalDate endDate = LocalDate.parse(currentParams.getEndDate(), COMPACT_FORMATTER);

        // 股市开盘时间 09:24:00 (包含集合竞价从09:24开始)
        LocalDateTime start = LocalDateTime.of(startDate, LocalTime.of(9, 24, 0));
        // 股市收盘时间 15:05:00 (包含盘后交易到15:05)
        LocalDateTime end = LocalDateTime.of(endDate, LocalTime.of(15, 5, 0));

        virtualTime = start.toEpochSecond(BEIJING_ZONE);
        preloadCursor = start;
        totalSentCount = 0;

        // 启动预加载线程
        CompletableFuture<Void> preloaderFuture = startPreloader(end);

        // 等待初始数据加载
        waitForInitialData();

        // 主回放循环
        long endTimestamp = end.toEpochSecond(BEIJING_ZONE);
        long lastLogTime = System.currentTimeMillis();

        while (virtualTime <= endTimestamp && running.get()) {
            // 处理暂停
            while (paused.get() && running.get()) {
                sleep(100);
            }

            // 跳过非交易时间（11:30-13:00）
            if (isNonTradingTime(virtualTime)) {
                virtualTime += 1;
                continue;
            }

            // 1. 从缓冲区获取当前秒的数据
            List<HistoryTrendDTO> batch = buffer.pollSlice(virtualTime);

            // 2. 发送到 Kafka
            if (batch != null && !batch.isEmpty()) {
                sendToKafka(batch);
                totalSentCount += batch.size();

                // --- 新增日志：确认推送时间和数据 ---
                if (log.isInfoEnabled()) {
                    LocalDateTime currentTime = LocalDateTime.ofEpochSecond(virtualTime, 0, BEIJING_ZONE);
                    log.info("推送数据|Push_data,virtualTime={},count={},firstStock={}",
                            currentTime, batch.size(), batch.get(0).getWindCode());
                }
                // ----------------------------------
            }

            // 3. 定期打印进度日志（每10秒）
            if (System.currentTimeMillis() - lastLogTime > 10000) {
                logProgress();
                lastLogTime = System.currentTimeMillis();
            }

            // 4. 休眠（根据速度倍数调整）
            sleepForReplay();

            // 5. 虚拟时间推进 1 秒
            virtualTime += 1;
        }

        log.info("=== 回放完成|Replay_finished,totalSent={} ===", totalSentCount);
    }

    /**
     * 启动预加载线程
     * <p>
     * 异步从数据库批量加载数据到缓冲区，确保主线程回放时不会因为 I/O 阻塞。
     * 当缓冲区满时，会自动等待。
     *
     * @param endDate 回放结束时间
     * @return CompletableFuture 用于追踪线程状态
     */
    private CompletableFuture<Void> startPreloader(LocalDateTime endDate) {
        return CompletableFuture.runAsync(() -> {
            log.info("预加载线程启动|Preloader_start");
            int preloadMin = currentParams.getPreloadMinutes() != null ? currentParams.getPreloadMinutes() : 5;
            int bufferMax = currentParams.getBufferMaxSize() != null ? currentParams.getBufferMaxSize() : 100000;

            while (preloadCursor.isBefore(endDate) && running.get()) {
                // 如果缓冲区数据太多，等待消费
                while (buffer.size() > bufferMax && running.get()) {
                    log.debug("缓冲区已满，等待消费|Buffer_full,size={}", buffer.size());
                    sleep(500);
                }
                if (!running.get()) break;
                LocalDateTime sliceEnd = preloadCursor.plusMinutes(preloadMin);
                if (sliceEnd.isAfter(endDate)) {
                    sliceEnd = endDate;
                }
                try {
                    List<HistoryTrendDTO> data = dataLoader.loadTimeSlice(preloadCursor, sliceEnd);
                    if (!data.isEmpty()) {
                        buffer.addBatch(data);
                        log.info("预加载完成|Preload_done,range={}-{},count={},bufferSize={}",
                                preloadCursor, sliceEnd, data.size(), buffer.size());
                    }
                } catch (Exception e) {
                    log.error("预加载失败|Preload_error,range={}-{}", preloadCursor, sliceEnd, e);
                }

                preloadCursor = sliceEnd;
            }

            log.info("预加载线程结束|Preloader_end");
        });
    }

    /**
     * 等待初始数据加载
     * <p>
     * 阻塞主回放线程，直到缓冲区中有足够的数据或超时（60秒）。
     */
    private void waitForInitialData() {
        log.info("等待初始数据加载...|Waiting_initial_data");
        int waitCount = 0;
        while (buffer.isEmpty() && running.get() && waitCount < 60) {
            sleep(1000);
            waitCount++;
        }
        log.info("初始数据就绪，开始回放|Initial_data_ready,bufferSize={}", buffer.size());
    }

    /**
     * 发送数据到 Kafka
     *
     * @param batch 历史行情数据批次
     */
    private void sendToKafka(List<HistoryTrendDTO> batch) {
        String topic = KafkaTopics.QUOTATION.code();
        kafkaProducer.sendBatchHighPerformance(topic, batch);
    }

    /**
     * 根据速度倍数休眠
     * <p>
     * 用于控制回放速度。
     * 例如 1倍速 = 休眠1000ms，10倍速 = 休眠100ms。
     * 0倍速不休眠。
     */
    private void sleepForReplay() {
        if (currentParams == null) return;
        int speed = currentParams.getSpeedMultiplier() != null ? currentParams.getSpeedMultiplier() : 1;
        if (speed <= 0) {
            // 最快速度，不休眠
            return;
        }
        long sleepMs = 1000 / speed;
        if (sleepMs > 0) {
            sleep(sleepMs);
        }
    }

    /**
     * 判断是否为非交易时间（午休 11:30-13:00）
     *
     * @param timestamp 当前虚拟时间戳
     * @return true如果是午休时间
     */
    private boolean isNonTradingTime(long timestamp) {
        LocalDateTime time = LocalDateTime.ofEpochSecond(timestamp, 0, BEIJING_ZONE);
        LocalTime localTime = time.toLocalTime();

        // 午休时间：11:30:00 - 13:00:00
        LocalTime lunchStart = LocalTime.of(11, 30, 0);
        LocalTime lunchEnd = LocalTime.of(13, 0, 0);

        return !localTime.isBefore(lunchStart) && localTime.isBefore(lunchEnd);
    }

    /**
     * 打印进度日志
     */
    private void logProgress() {
        LocalDateTime currentVirtualTime = LocalDateTime.ofEpochSecond(virtualTime, 0, BEIJING_ZONE);
        log.info("回放进度|Replay_progress,virtualTime={},bufferSize={},totalSent={}",
                currentVirtualTime, buffer.size(), totalSentCount);
    }

    /**
     * 暂停回放
     * <p>
     * 设置暂停标志位，主循环将挂起。
     */
    public void pause() {
        paused.set(true);
        log.info("回放已暂停|Replay_paused");
    }

    /**
     * 恢复回放
     * <p>
     * 清除暂停标志位，主循环将继续。
     */
    public void resume() {
        paused.set(false);
        log.info("回放已恢复|Replay_resumed");
    }

    /**
     * 停止回放
     * <p>
     * 停止主循环和预加载线程，并清空缓冲区。
     */
    public void stop() {
        running.set(false);
        buffer.clear();
        log.info("回放已停止|Replay_stopped");
    }

    /**
     * 动态调整回放速度（运行时）
     * @param multiplier 速度倍数
     */
    public void updateSpeed(int multiplier) {
        if (running.get() && currentParams != null) {
            currentParams.setSpeedMultiplier(multiplier);
            log.info("运行时速度已调整为 {}x", multiplier);
        }
    }

    /**
     * 获取当前状态
     *
     * @return 回放状态快照
     */
    public ReplayStatus getStatus() {
        return new ReplayStatus(
                running.get(),
                paused.get(),
                virtualTime,
                buffer.size(),
                totalSentCount,
                currentParams // 传入当前运行的配置
        );
    }

    /**
     * 回放状态信息
     *
     * @param running          是否正在运行
     * @param paused           是否已暂停
     * @param virtualTimestamp 当前虚拟时间戳（秒）
     * @param bufferSize       当前缓冲区大小
     * @param totalSentCount   累计发送消息数
     * @param activeConfig     当前运行的回放配置（可能为null）
     */
    public record ReplayStatus(
            boolean running,
            boolean paused,
            long virtualTimestamp,
            int bufferSize,
            long totalSentCount,
            ReplayParamsDTO activeConfig
    ) {
        /**
         * 获取格式化的虚拟时间字符串
         * @return ISO-8601 格式的时间字符串
         */
        public String getVirtualTimeStr() {
            return Instant.ofEpochSecond(virtualTimestamp).toString();
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
