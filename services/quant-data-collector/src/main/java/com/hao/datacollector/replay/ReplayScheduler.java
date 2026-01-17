package com.hao.datacollector.replay;

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
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@Component
public class ReplayScheduler {

    private final DataLoader dataLoader;
    private final TimeSliceBuffer buffer;
    private final KafkaProducerService kafkaProducer;
    private final ReplayProperties config;

    /**
     * 北京时区偏移量
     */
    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");

    private static final DateTimeFormatter COMPACT_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    /**
     * 虚拟时间（模拟的当前时间，秒级时间戳）
     */
    private volatile long virtualTime;

    /**
     * 预加载游标
     */
    private volatile LocalDateTime preloadCursor;

    /**
     * 运行状态标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 暂停状态标志
     */
    private final AtomicBoolean paused = new AtomicBoolean(false);

    /**
     * 回放统计：已推送的消息数
     */
    private volatile long totalSentCount = 0;

    /**
     * 显式构造函数，确保依赖注入成功
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
     * 启动回放（异步执行）
     */
    @Async("ioTaskExecutor")
    public void startReplay() {
        // 双重检查，防止 config 为 null (虽然构造函数已经检查了)
        if (config == null) {
            log.error("严重错误：ReplayProperties 为 null，无法启动回放！");
            return;
        }

        if (!config.isEnabled()) {
            log.info("回放模式未启用|Replay_mode_disabled");
            return;
        }

        if (running.compareAndSet(false, true)) {
            try {
                doReplay();
            } catch (Exception e) {
                log.error("回放过程中发生未捕获异常|Replay_exception", e);
            } finally {
                running.set(false);
                log.info("回放任务结束，状态已重置|Replay_task_ended");
            }
        } else {
            log.warn("回放已在运行中|Replay_already_running");
        }
    }

    /**
     * 执行回放主流程
     */
    private void doReplay() {
        log.info("=== 回放服务启动|Replay_service_start ===");
        log.info("配置信息|Config,startDate={},endDate={},speed={}x,preloadMinutes={}",
                config.getStartDate(), config.getEndDate(),
                config.getSpeedMultiplier(), config.getPreloadMinutes());

        // 初始化时间参数
        LocalDate startDate = LocalDate.parse(config.getStartDate(), COMPACT_FORMATTER);
        LocalDate endDate = LocalDate.parse(config.getEndDate(), COMPACT_FORMATTER);

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
     */
    private CompletableFuture<Void> startPreloader(LocalDateTime endDate) {
        return CompletableFuture.runAsync(() -> {
            log.info("预加载线程启动|Preloader_start");
            while (preloadCursor.isBefore(endDate) && running.get()) {
                // 如果缓冲区数据太多，等待消费
                while (buffer.size() > config.getBufferMaxSize() && running.get()) {
                    log.debug("缓冲区已满，等待消费|Buffer_full,size={}", buffer.size());
                    sleep(500);
                }
                if (!running.get()) break;
                LocalDateTime sliceEnd = preloadCursor.plusMinutes(config.getPreloadMinutes());
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
     */
    private void sendToKafka(List<HistoryTrendDTO> batch) {
        String topic = KafkaTopics.QUOTATION.code();
        kafkaProducer.sendBatchHighPerformance(topic, batch);
    }

    /**
     * 根据速度倍数休眠
     */
    private void sleepForReplay() {
        int speed = config.getSpeedMultiplier();
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
     */
    public void pause() {
        paused.set(true);
        log.info("回放已暂停|Replay_paused");
    }

    /**
     * 恢复回放
     */
    public void resume() {
        paused.set(false);
        log.info("回放已恢复|Replay_resumed");
    }

    /**
     * 停止回放
     */
    public void stop() {
        running.set(false);
        buffer.clear();
        log.info("回放已停止|Replay_stopped");
    }

    /**
     * 获取当前状态
     */
    public ReplayStatus getStatus() {
        return new ReplayStatus(
                running.get(),
                paused.get(),
                virtualTime,
                buffer.size(),
                totalSentCount
        );
    }

    /**
     * 回放状态信息
     */
    public record ReplayStatus(
            boolean running,
            boolean paused,
            long virtualTimestamp,
            int bufferSize,
            long totalSentCount
    ) {
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
