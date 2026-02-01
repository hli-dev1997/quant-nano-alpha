package com.hao.datacollector.replay;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.replay.ReplayParamsDTO;
import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.replay.preheat.StrategyPreheaterManager;
import com.hao.datacollector.service.KafkaProducerService;
import com.hao.datacollector.service.QuotationService;
import constants.DateTimeFormatConstants;
import enums.market.RiskMarketIndexEnum;
import integration.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 行情回放调度器（精简版）
 * <p>
 * 核心功能：
 * 1. 查询历史行情数据库
 * 2. 按 tradeDate 分组推送到 Kafka
 * 3. 支持倍速控制、暂停/恢复/停止
 * 4. 股票→策略模块(quotation)，指数→风控模块(quotation_index)
 *
 * @author hli
 * @date 2026-01-20
 */
@Slf4j
@Component
public class ReplayScheduler {

    private final QuotationService quotationService;
    private final KafkaProducerService kafkaProducer;
    private final ReplayProperties config;
    private final StrategyPreheaterManager preheaterManager;

    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");
    private static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern(DateTimeFormatConstants.DEFAULT_DATETIME_FORMAT);

    // 运行状态
    private volatile ReplayParamsDTO currentParams;
    private volatile long virtualTime;
    private volatile long totalSentCount = 0;
    private volatile long indexSentCount = 0;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    // 内存缓存：按秒分组的行情数据
    private final TreeMap<Long, List<HistoryTrendDTO>> stockBuffer = new TreeMap<>();
    private final TreeMap<Long, List<HistoryTrendDTO>> indexBuffer = new TreeMap<>();

    public ReplayScheduler(QuotationService quotationService,
                           KafkaProducerService kafkaProducer,
                           ReplayProperties config,
                           StrategyPreheaterManager preheaterManager) {
        Assert.notNull(quotationService, "QuotationService must not be null");
        Assert.notNull(kafkaProducer, "KafkaProducerService must not be null");
        Assert.notNull(config, "ReplayProperties must not be null");
        Assert.notNull(preheaterManager, "StrategyPreheaterManager must not be null");

        this.quotationService = quotationService;
        this.kafkaProducer = kafkaProducer;
        this.config = config;
        this.preheaterManager = preheaterManager;

        log.info("ReplayScheduler初始化完成|ReplayScheduler_initialized");
    }

    // ==================== 公开接口 ====================

    @Async("ioTaskExecutor")
    public void startReplay() {
        if (!config.isEnabled()) {
            log.info("回放模式未启用|Replay_mode_disabled");
            return;
        }
        startReplay(buildParamsFromConfig());
    }

    @Async("ioTaskExecutor")
    public void startReplay(ReplayParamsDTO params) {
        if (params == null) {
            log.error("启动失败：参数不能为null");
            return;
        }

        if (running.compareAndSet(false, true)) {
            try {
                this.currentParams = params;
                doReplay();
            } catch (Exception e) {
                log.error("回放异常|Replay_exception", e);
            } finally {
                running.set(false);
                currentParams = null;
                stockBuffer.clear();
                indexBuffer.clear();
                log.info("回放任务结束|Replay_task_ended");
            }
        } else {
            log.warn("回放已在运行中|Replay_already_running");
        }
    }

    public void pause() {
        paused.set(true);
        log.info("回放已暂停|Replay_paused");
    }

    public void resume() {
        paused.set(false);
        log.info("回放已恢复|Replay_resumed");
    }

    public void stop() {
        running.set(false);
        log.info("回放已停止|Replay_stopped");
    }

    public void updateSpeed(int multiplier) {
        if (running.get() && currentParams != null) {
            currentParams.setSpeedMultiplier(multiplier);
            log.info("速度已调整为{}x", multiplier);
        }
    }

    public ReplayStatus getStatus() {
        return new ReplayStatus(running.get(), paused.get(), virtualTime,
                stockBuffer.values().stream().mapToInt(List::size).sum(),
                totalSentCount, currentParams);
    }

    // ==================== 核心回放逻辑 ====================

    private void doReplay() {
        log.info("=== 回放服务启动|Replay_start ===");
        log.info("参数|Params,start={},end={},speed={}x",
                currentParams.getStartDate(), currentParams.getEndDate(), currentParams.getSpeedMultiplier());

        LocalDate startDate = LocalDate.parse(currentParams.getStartDate(), COMPACT_FORMATTER);
        LocalDate endDate = LocalDate.parse(currentParams.getEndDate(), COMPACT_FORMATTER);

        // [FULL_CHAIN_STEP_02] 策略数据预热 - 预缓存九转等策略所需历史数据到 Redis
        // @see docs/architecture/FullChainDataFlow.md
        List<String> stockCodes = parseStockCodes(currentParams.getStockCodes());
        preheaterManager.preheatAll(startDate, stockCodes);

        // [FULL_CHAIN_STEP_03] 加载历史行情数据到内存 (MySQL → TreeMap)
        loadAllData(startDate, endDate);

        // 3. 主循环：按秒推送
        LocalDateTime start = LocalDateTime.of(startDate, LocalTime.of(9, 24, 0));
        LocalDateTime end = LocalDateTime.of(endDate, LocalTime.of(15, 5, 0));
        virtualTime = start.toEpochSecond(BEIJING_ZONE);
        long endTimestamp = end.toEpochSecond(BEIJING_ZONE);
        totalSentCount = 0;
        indexSentCount = 0;

        while (virtualTime <= endTimestamp && running.get()) {
            // 暂停处理
            while (paused.get() && running.get()) {
                sleep(100);
            }

            // 跳过午休 11:30-13:00
            if (isLunchBreak(virtualTime)) {
                virtualTime++;
                continue;
            }

            // [FULL_CHAIN_STEP_04] 推送股票行情到 Kafka → 策略引擎消费
            List<HistoryTrendDTO> stocks = stockBuffer.remove(virtualTime);
            if (stocks != null && !stocks.isEmpty()) {
                kafkaProducer.sendBatchHighPerformance(KafkaTopics.QUOTATION.code(), stocks);
                totalSentCount += stocks.size();
            }

            // [FULL_CHAIN_STEP_05] 推送指数行情到 Kafka → 风控模块消费
            List<HistoryTrendDTO> indices = indexBuffer.remove(virtualTime);
            if (indices != null && !indices.isEmpty()) {
                kafkaProducer.sendBatchHighPerformance(KafkaTopics.QUOTATION_INDEX.code(), indices);
                indexSentCount += indices.size();
            }

            // 倍速休眠
            sleepForSpeed();
            virtualTime++;
        }

        log.info("=== 回放完成|Replay_done,stockSent={},indexSent={} ===", totalSentCount, indexSentCount);
    }

    private void loadAllData(LocalDate startDate, LocalDate endDate) {
        log.info("加载数据|Loading_data,start={},end={}", startDate, endDate);

        String startStr = LocalDateTime.of(startDate, LocalTime.of(9, 24, 0)).format(FULL_FORMATTER);
        String endStr = LocalDateTime.of(endDate, LocalTime.of(15, 5, 0)).format(FULL_FORMATTER);

        // 加载股票数据
        List<String> stockCodes = parseStockCodes(currentParams.getStockCodes());
        List<HistoryTrendDTO> stockData = quotationService.getHistoryTrendDataByTimeRange(startStr, endStr, stockCodes);
        if (stockData != null) {
            groupByTimestamp(stockData, stockBuffer);
            log.info("股票数据加载完成|Stock_loaded,count={}", stockData.size());
        }

        // 加载指数数据
        List<String> indexCodes = Arrays.stream(RiskMarketIndexEnum.values())
                .map(RiskMarketIndexEnum::getCode).collect(Collectors.toList());
        List<HistoryTrendDTO> indexData = quotationService.getIndexHistoryTrendDataByTimeRange(startStr, endStr, indexCodes);
        if (indexData != null) {
            groupByTimestamp(indexData, indexBuffer);
            log.info("指数数据加载完成|Index_loaded,count={}", indexData.size());
        }
    }

    private void groupByTimestamp(List<HistoryTrendDTO> data, TreeMap<Long, List<HistoryTrendDTO>> buffer) {
        for (HistoryTrendDTO dto : data) {
            if (dto.getTradeDate() != null) {
                long ts = dto.getTradeDate().toEpochSecond(BEIJING_ZONE);
                // 设置 traceId 用于全链路追踪（格式: yyyyMMdd_HHmmss）
                String traceId = dto.getTradeDate().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                dto.setTraceId(traceId);
                buffer.computeIfAbsent(ts, k -> new ArrayList<>()).add(dto);
            }
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isLunchBreak(long timestamp) {
        LocalTime time = LocalDateTime.ofEpochSecond(timestamp, 0, BEIJING_ZONE).toLocalTime();
        return !time.isBefore(LocalTime.of(11, 30)) && time.isBefore(LocalTime.of(13, 0));
    }

    private void sleepForSpeed() {
        if (currentParams == null) return;
        int speed = currentParams.getSpeedMultiplier() != null ? currentParams.getSpeedMultiplier() : 1;
        if (speed <= 0) return; // 最快速度
        sleep(1000 / speed);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private List<String> parseStockCodes(String stockCodesStr) {
        if (!StringUtils.hasLength(stockCodesStr)) return Collections.emptyList();
        return Arrays.asList(stockCodesStr.split(","));
    }

    private ReplayParamsDTO buildParamsFromConfig() {
        ReplayParamsDTO params = new ReplayParamsDTO();
        params.setStartDate(config.getStartDate());
        params.setEndDate(config.getEndDate());
        params.setSpeedMultiplier(config.getSpeedMultiplier());
        params.setStockCodes(config.getStockCodes());
        params.setPreloadMinutes(config.getPreloadMinutes());
        params.setBufferMaxSize(config.getBufferMaxSize());
        return params;
    }

    // ==================== 状态记录 ====================

    public record ReplayStatus(boolean running, boolean paused, long virtualTimestamp,
                               int bufferSize, long totalSentCount, ReplayParamsDTO activeConfig) {
        public String getVirtualTimeStr() {
            return Instant.ofEpochSecond(virtualTimestamp).toString();
        }
    }
}
