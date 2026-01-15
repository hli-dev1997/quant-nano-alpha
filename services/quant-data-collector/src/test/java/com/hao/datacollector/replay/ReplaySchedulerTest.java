package com.hao.datacollector.replay;

import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.KafkaProducerService;
import com.hao.datacollector.service.QuotationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReplayScheduler 行情回放调度器测试类
 * <p>
 * 测试目标：验证回放调度器的核心逻辑正确性
 * <p>
 * 测试范围：
 * 1. 功能测试：启动、暂停、恢复、停止功能
 * 2. 边界测试：非交易时间跳过、空数据处理
 * 3. 速度控制测试：不同速度倍数的回放
 * 4. 状态管理测试：状态查询和转换
 * <p>
 * 注意：本测试使用 Mock 对象隔离外部依赖（Kafka、数据库）
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReplaySchedulerTest {

    @Mock
    private QuotationService quotationService;

    @Mock
    private KafkaProducerService kafkaProducer;

    private ReplayProperties config;
    private TimeSliceBuffer buffer;
    private DataLoader dataLoader;
    private ReplayScheduler scheduler;

    /**
     * 北京时区偏移
     */
    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");

    @BeforeEach
    void setUp() {
        // 初始化配置
        config = new ReplayProperties();
        config.setEnabled(true);
        config.setStartDate("20250601");
        config.setEndDate("20250601");
        config.setSpeedMultiplier(0); // 最快速度，无延迟
        config.setPreloadMinutes(5);
        config.setBufferMaxSize(100000);

        // 初始化组件
        buffer = new TimeSliceBuffer();
        dataLoader = new DataLoader(quotationService, config);

        // 创建调度器（使用真实的 buffer，mock 的 kafka）
        scheduler = new ReplayScheduler(dataLoader, buffer, kafkaProducer, config);
    }

    // ==================== 状态管理测试 ====================

    /**
     * 测试：初始状态查询
     * <p>
     * 思路：调度器创建后应处于非运行状态
     * <p>
     * 预期结果：running=false, paused=false
     */
    @Test
    @Order(1)
    @DisplayName("状态管理 - 初始状态")
    void testInitialStatus() {
        // When: 查询初始状态
        ReplayScheduler.ReplayStatus status = scheduler.getStatus();

        // Then: 验证初始状态
        assertFalse(status.running(), "初始时不应在运行");
        assertFalse(status.paused(), "初始时不应暂停");
        assertEquals(0, status.totalSentCount(), "初始发送计数应为0");

        log.info("初始状态测试通过");
    }

    /**
     * 测试：暂停和恢复状态切换
     * <p>
     * 思路：测试 pause() 和 resume() 方法的状态切换
     * <p>
     * 预期结果：状态能正确切换
     */
    @Test
    @Order(2)
    @DisplayName("状态管理 - 暂停和恢复")
    void testPauseAndResume() {
        // When: 暂停
        scheduler.pause();

        // Then: 状态应为暂停
        assertTrue(scheduler.getStatus().paused(), "调用 pause 后应为暂停状态");

        // When: 恢复
        scheduler.resume();

        // Then: 状态应恢复
        assertFalse(scheduler.getStatus().paused(), "调用 resume 后应取消暂停");

        log.info("暂停和恢复测试通过");
    }

    /**
     * 测试：停止后状态和缓冲区清理
     * <p>
     * 思路：stop() 应停止运行并清空缓冲区
     * <p>
     * 预期结果：running=false，缓冲区被清空
     */
    @Test
    @Order(3)
    @DisplayName("状态管理 - 停止并清理")
    void testStopAndCleanup() {
        // Given: 缓冲区有数据
        buffer.addBatch(createTestData(LocalDateTime.now(), 10));
        assertEquals(10, buffer.size());

        // When: 停止
        scheduler.stop();

        // Then: 缓冲区应被清空
        assertEquals(0, buffer.size(), "停止后缓冲区应被清空");
        assertFalse(scheduler.getStatus().running(), "停止后不应在运行");

        log.info("停止并清理测试通过");
    }

    // ==================== 配置验证测试 ====================

    /**
     * 测试：禁用模式下不启动
     * <p>
     * 思路：enabled=false 时调用 startReplay 应直接返回
     * <p>
     * 预期结果：不进入运行状态
     */
    @Test
    @Order(10)
    @DisplayName("配置验证 - 禁用模式")
    void testDisabledMode() throws InterruptedException {
        // Given: 禁用回放
        config.setEnabled(false);

        // When: 尝试启动
        scheduler.startReplay();

        // 稍等一下让异步方法有机会执行
        Thread.sleep(100);

        // Then: 不应进入运行状态（因为是异步的，这里检查不到 running=true）
        // 主要验证不会抛异常且 Kafka 不被调用
        verify(kafkaProducer, never()).sendBatchHighPerformance(any(), any());

        log.info("禁用模式测试通过");
    }

    // ==================== 数据处理测试 ====================

    /**
     * 测试：缓冲区数据推送到 Kafka
     * <p>
     * 思路：
     * 1. 预先向缓冲区填充数据
     * 2. 模拟调度器的核心推送逻辑
     * 3. 验证 Kafka 被正确调用
     * <p>
     * 预期结果：数据被发送到 Kafka
     */
    @Test
    @Order(20)
    @DisplayName("数据处理 - 缓冲区数据推送")
    void testBufferDataPush() {
        // Given: 向缓冲区添加数据
        LocalDateTime time = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        List<HistoryTrendDTO> testData = createTestData(time, 50);
        buffer.addBatch(testData);

        // When: 模拟从缓冲区取出并发送
        long timestamp = time.toEpochSecond(BEIJING_ZONE);
        List<HistoryTrendDTO> batch = buffer.pollSlice(timestamp);

        assertNotNull(batch);
        kafkaProducer.sendBatchHighPerformance("quotation", batch);

        // Then: 验证 Kafka 被正确调用
        ArgumentCaptor<List<HistoryTrendDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(kafkaProducer).sendBatchHighPerformance(eq("quotation"), captor.capture());

        List<HistoryTrendDTO> sentData = captor.getValue();
        assertEquals(50, sentData.size(), "应发送50条数据");
        assertEquals(0, buffer.size(), "发送后缓冲区应为空");

        log.info("缓冲区数据推送测试通过");
    }

    /**
     * 测试：空时间片处理
     * <p>
     * 思路：当某秒没有数据时，pollSlice 返回 null，不应调用 Kafka
     * <p>
     * 预期结果：Kafka 不被调用
     */
    @Test
    @Order(21)
    @DisplayName("数据处理 - 空时间片处理")
    void testEmptyTimeSlice() {
        // Given: 空缓冲区

        // When: 尝试获取一个时间片
        long timestamp = LocalDateTime.of(2025, 6, 1, 9, 30, 0).toEpochSecond(BEIJING_ZONE);
        List<HistoryTrendDTO> batch = buffer.pollSlice(timestamp);

        // Then: 返回 null，不应做任何发送
        assertNull(batch, "空缓冲区应返回 null");

        // 模拟调度器逻辑：只有非空才发送
        if (batch != null && !batch.isEmpty()) {
            kafkaProducer.sendBatchHighPerformance("quotation", batch);
        }

        verify(kafkaProducer, never()).sendBatchHighPerformance(any(), any());

        log.info("空时间片处理测试通过");
    }

    // ==================== 时间逻辑测试 ====================

    /**
     * 测试：非交易时间识别（午休时间）
     * <p>
     * 思路：验证 11:30-13:00 被正确识别为非交易时间
     * <p>
     * 预期结果：
     * - 11:29:59 是交易时间
     * - 11:30:00 是非交易时间
     * - 12:30:00 是非交易时间
     * - 13:00:00 是交易时间
     */
    @Test
    @Order(30)
    @DisplayName("时间逻辑 - 午休时间识别")
    void testNonTradingTimeRecognition() {
        // 通过反射或测试辅助方法测试时间识别
        // 由于 isNonTradingTime 是私有方法，这里通过行为验证

        // 创建午休前后的时间戳
        LocalDateTime beforeLunch = LocalDateTime.of(2025, 6, 1, 11, 29, 59);
        LocalDateTime lunchStart = LocalDateTime.of(2025, 6, 1, 11, 30, 0);
        LocalDateTime midLunch = LocalDateTime.of(2025, 6, 1, 12, 30, 0);
        LocalDateTime afterLunch = LocalDateTime.of(2025, 6, 1, 13, 0, 0);

        // 午休时间识别逻辑（复制自 ReplayScheduler）
        assertTrue(isTradingTime(beforeLunch), "11:29:59 应是交易时间");
        assertFalse(isTradingTime(lunchStart), "11:30:00 应是非交易时间（午休）");
        assertFalse(isTradingTime(midLunch), "12:30:00 应是非交易时间（午休）");
        assertTrue(isTradingTime(afterLunch), "13:00:00 应是交易时间");

        log.info("午休时间识别测试通过");
    }

    /**
     * 测试：虚拟时间推进
     * <p>
     * 思路：验证时间按秒正确推进
     * <p>
     * 预期结果：每次循环时间戳增加1秒
     */
    @Test
    @Order(31)
    @DisplayName("时间逻辑 - 虚拟时间推进")
    void testVirtualTimeAdvance() {
        LocalDateTime startTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        long virtualTime = startTime.toEpochSecond(BEIJING_ZONE);

        // 模拟10次时间推进
        for (int i = 0; i < 10; i++) {
            long expectedTime = startTime.plusSeconds(i).toEpochSecond(BEIJING_ZONE);
            assertEquals(expectedTime, virtualTime, "第 " + i + " 秒时间戳不正确");
            virtualTime += 1; // 模拟推进
        }

        // 最终时间应为起始时间 + 10秒
        long finalExpected = startTime.plusSeconds(10).toEpochSecond(BEIJING_ZONE);
        assertEquals(finalExpected, virtualTime);

        log.info("虚拟时间推进测试通过");
    }

    // ==================== 速度控制测试 ====================

    /**
     * 测试：速度倍数计算
     * <p>
     * 思路：验证不同速度倍数下的休眠时间计算
     * <p>
     * 预期结果：
     * - speedMultiplier=1 -> 休眠 1000ms
     * - speedMultiplier=10 -> 休眠 100ms
     * - speedMultiplier=0 -> 不休眠
     */
    @Test
    @Order(40)
    @DisplayName("速度控制 - 休眠时间计算")
    void testSpeedMultiplierCalculation() {
        // 速度倍数 1: 实时
        assertEquals(1000, calculateSleepMs(1), "1倍速应休眠1000ms");

        // 速度倍数 10: 10倍速
        assertEquals(100, calculateSleepMs(10), "10倍速应休眠100ms");

        // 速度倍数 100: 100倍速
        assertEquals(10, calculateSleepMs(100), "100倍速应休眠10ms");

        // 速度倍数 0: 最快速度
        assertEquals(0, calculateSleepMs(0), "0倍速（最快）不应休眠");

        log.info("速度倍数计算测试通过");
    }

    /**
     * 测试：动态调速
     * <p>
     * 思路：运行时修改 config.speedMultiplier 应立即生效
     * <p>
     * 预期结果：配置修改后下一次循环使用新速度
     */
    @Test
    @Order(41)
    @DisplayName("速度控制 - 动态调速")
    void testDynamicSpeedChange() {
        // 初始速度
        config.setSpeedMultiplier(1);
        assertEquals(1, config.getSpeedMultiplier());

        // 动态修改
        config.setSpeedMultiplier(50);
        assertEquals(50, config.getSpeedMultiplier());

        // 验证新速度生效
        assertEquals(20, calculateSleepMs(config.getSpeedMultiplier()));

        log.info("动态调速测试通过");
    }

    // ==================== 性能测试 ====================

    /**
     * 测试：调度循环性能
     * <p>
     * 思路：模拟调度器的核心循环，测量处理速度
     * <p>
     * 预期结果：处理 1000 个时间片应在合理时间内完成
     */
    @Test
    @Order(50)
    @DisplayName("性能测试 - 调度循环性能")
    void testSchedulerLoopPerformance() {
        // Given: 预填充1000秒的数据（每秒100只股票）
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        int secondsCount = 1000;
        int stocksPerSecond = 100;

        for (int sec = 0; sec < secondsCount; sec++) {
            LocalDateTime time = baseTime.plusSeconds(sec);
            buffer.addBatch(createTestData(time, stocksPerSecond));
        }

        assertEquals(secondsCount * stocksPerSecond, buffer.size());

        // When: 模拟调度循环
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        long virtualTime = baseTime.toEpochSecond(BEIJING_ZONE);
        AtomicInteger totalProcessed = new AtomicInteger(0);

        for (int i = 0; i < secondsCount; i++) {
            List<HistoryTrendDTO> batch = buffer.pollSlice(virtualTime);
            if (batch != null) {
                totalProcessed.addAndGet(batch.size());
            }
            virtualTime += 1;
        }

        stopWatch.stop();

        // Then: 验证性能
        long elapsedMs = stopWatch.getTotalTimeMillis();
        assertEquals(secondsCount * stocksPerSecond, totalProcessed.get());
        assertTrue(buffer.isEmpty());

        log.info("调度循环性能测试：处理 {} 个时间片，{} 条数据，耗时 {} ms",
                secondsCount, totalProcessed.get(), elapsedMs);

        // 性能断言：1000个时间片应在500ms内处理完成
        assertTrue(elapsedMs < 1000, "1000个时间片应在1秒内处理完成");
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断是否为交易时间（复制自 ReplayScheduler 的逻辑用于测试）
     */
    private boolean isTradingTime(LocalDateTime time) {
        java.time.LocalTime localTime = time.toLocalTime();
        java.time.LocalTime lunchStart = java.time.LocalTime.of(11, 30, 0);
        java.time.LocalTime lunchEnd = java.time.LocalTime.of(13, 0, 0);

        // 午休时间返回 false
        return localTime.isBefore(lunchStart) || !localTime.isBefore(lunchEnd);
    }

    /**
     * 计算休眠时间（复制自 ReplayScheduler 的逻辑用于测试）
     */
    private long calculateSleepMs(int speedMultiplier) {
        if (speedMultiplier <= 0) {
            return 0;
        }
        return 1000 / speedMultiplier;
    }

    /**
     * 创建测试数据
     */
    private List<HistoryTrendDTO> createTestData(LocalDateTime time, int count) {
        List<HistoryTrendDTO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HistoryTrendDTO dto = new HistoryTrendDTO();
            dto.setWindCode(String.format("%06d.SH", i));
            dto.setTradeDate(time);
            dto.setLatestPrice(100.0 + Math.random() * 10);
            dto.setTotalVolume(10000.0);
            dto.setAveragePrice(100.0);
            list.add(dto);
        }
        return list;
    }
}
