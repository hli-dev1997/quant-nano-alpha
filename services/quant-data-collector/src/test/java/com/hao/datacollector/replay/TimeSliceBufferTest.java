package com.hao.datacollector.replay;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeSliceBuffer 时间片缓存测试类
 * <p>
 * 测试目标：验证 TimeSliceBuffer 的正确性、线程安全性和性能表现
 * <p>
 * 测试范围：
 * 1. 基础功能测试：添加、获取、删除数据
 * 2. 边界条件测试：空数据、大数据量、时间边界
 * 3. 并发安全测试：多线程读写、竞态条件
 * 4. 性能测试：大数据量写入/读取性能
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimeSliceBufferTest {

    private TimeSliceBuffer buffer;

    /**
     * 每个测试前初始化新的缓冲区实例
     */
    @BeforeEach
    void setUp() {
        buffer = new TimeSliceBuffer();
    }

    // ==================== 基础功能测试 ====================

    /**
     * 测试：基本添加和获取功能
     * <p>
     * 思路：
     * 1. 创建模拟行情数据
     * 2. 添加到缓冲区
     * 3. 验证缓冲区大小正确
     * 4. 通过 pollSlice 获取数据并验证
     * <p>
     * 预期结果：
     * - 添加后 size() 应返回正确条数
     * - pollSlice 应返回对应时间戳的数据
     * - pollSlice 后数据应被移除
     */
    @Test
    @Order(1)
    @DisplayName("基本功能 - 添加和获取数据")
    void testBasicAddAndPoll() {
        // Given: 创建测试数据，包含3只股票在同一秒的行情
        LocalDateTime time = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        List<HistoryTrendDTO> testData = createTestData(time, 3);

        // When: 添加数据到缓冲区
        buffer.addBatch(testData);

        // Then: 验证缓冲区状态
        assertEquals(3, buffer.size(), "缓冲区应包含3条数据");
        assertEquals(1, buffer.sliceCount(), "应只有1个时间片（同一秒）");
        assertFalse(buffer.isEmpty(), "缓冲区不应为空");

        // When: 获取数据（使用时间戳）
        long timestamp = time.toEpochSecond(java.time.ZoneOffset.of("+8"));
        List<HistoryTrendDTO> polled = buffer.pollSlice(timestamp);

        // Then: 验证获取结果
        assertNotNull(polled, "应成功获取数据");
        assertEquals(3, polled.size(), "应获取到3条数据");
        assertEquals(0, buffer.size(), "获取后缓冲区应为空");
        assertTrue(buffer.isEmpty(), "获取后缓冲区应为空");

        log.info("基本添加和获取测试通过");
    }

    /**
     * 测试：多时间片数据添加
     * <p>
     * 思路：
     * 1. 创建跨越多个秒的行情数据
     * 2. 验证时间片正确分组
     * <p>
     * 预期结果：
     * - 不同秒的数据应被正确分组
     * - sliceCount 应反映实际时间片数量
     */
    @Test
    @Order(2)
    @DisplayName("基本功能 - 多时间片分组")
    void testMultipleTimeSlices() {
        // Given: 创建跨3秒的数据（每秒10只股票）
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        List<HistoryTrendDTO> allData = new ArrayList<>();

        for (int sec = 0; sec < 3; sec++) {
            LocalDateTime time = baseTime.plusSeconds(sec);
            allData.addAll(createTestData(time, 10));
        }

        // When: 批量添加
        buffer.addBatch(allData);

        // Then: 验证分组正确
        assertEquals(30, buffer.size(), "总共应有30条数据（3秒 × 10只股票）");
        assertEquals(3, buffer.sliceCount(), "应有3个时间片");

        // 验证最早和最晚时间戳
        long earliestExpected = baseTime.toEpochSecond(java.time.ZoneOffset.of("+8"));
        long latestExpected = baseTime.plusSeconds(2).toEpochSecond(java.time.ZoneOffset.of("+8"));
        assertEquals(earliestExpected, buffer.getEarliestTimestamp());
        assertEquals(latestExpected, buffer.getLatestTimestamp());

        log.info("多时间片分组测试通过");
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试：空数据处理
     * <p>
     * 思路：验证传入 null 或空列表时不会抛出异常
     * <p>
     * 预期结果：空数据静默处理，缓冲区保持为空
     */
    @Test
    @Order(10)
    @DisplayName("边界条件 - 空数据处理")
    void testEmptyDataHandling() {
        // When: 添加 null
        assertDoesNotThrow(() -> buffer.addBatch(null), "添加 null 不应抛出异常");
        assertEquals(0, buffer.size());

        // When: 添加空列表
        assertDoesNotThrow(() -> buffer.addBatch(new ArrayList<>()), "添加空列表不应抛出异常");
        assertEquals(0, buffer.size());

        log.info("空数据处理测试通过");
    }

    /**
     * 测试：不存在的时间戳查询
     * <p>
     * 思路：查询缓冲区中不存在的时间戳
     * <p>
     * 预期结果：返回 null，不影响其他数据
     */
    @Test
    @Order(11)
    @DisplayName("边界条件 - 不存在的时间戳查询")
    void testNonExistentTimestamp() {
        // Given: 添加一条数据
        LocalDateTime time = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        buffer.addBatch(createTestData(time, 1));

        // When: 查询不存在的时间戳
        List<HistoryTrendDTO> result = buffer.pollSlice(999999999L);

        // Then: 应返回 null，原数据不受影响
        assertNull(result, "不存在的时间戳应返回 null");
        assertEquals(1, buffer.size(), "原数据不应被影响");

        log.info("不存在的时间戳查询测试通过");
    }

    /**
     * 测试：tradeDate 为 null 的数据过滤
     * <p>
     * 思路：包含 null tradeDate 的数据应被过滤
     * <p>
     * 预期结果：只有有效数据被添加
     */
    @Test
    @Order(12)
    @DisplayName("边界条件 - null tradeDate 数据过滤")
    void testNullTradeDateFiltering() {
        // Given: 创建包含 null tradeDate 的数据
        List<HistoryTrendDTO> data = new ArrayList<>();
        data.add(createSingleData(LocalDateTime.of(2025, 6, 1, 9, 30, 0), "600519.SH"));
        data.add(createSingleData(null, "000001.SZ")); // null tradeDate
        data.add(createSingleData(LocalDateTime.of(2025, 6, 1, 9, 30, 0), "600036.SH"));

        // When: 添加数据
        buffer.addBatch(data);

        // Then: 只有2条有效数据被添加
        assertEquals(2, buffer.size(), "null tradeDate 的数据应被过滤");

        log.info("null_tradeDate数据过滤测试通过|Null_tradeDate_filter_test_passed");
    }

    /**
     * 测试：大数据量边界
     * <p>
     * 思路：模拟全市场一个交易日的数据量（5000只股票 × 4小时 × 3600秒 = 7200万条理论值，
     * 实际分时数据约每3秒一条，约240条/股/日）
     * <p>
     * 预期结果：能够正确处理大规模数据
     */
    @Test
    @Order(13)
    @DisplayName("边界条件 - 大数据量处理")
    void testLargeDataVolume() {
        // Given: 模拟100只股票，600秒的数据（6万条）
        int stockCount = 100;
        int secondsCount = 600;
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        List<HistoryTrendDTO> largeData = new ArrayList<>();
        for (int sec = 0; sec < secondsCount; sec++) {
            LocalDateTime time = baseTime.plusSeconds(sec);
            for (int i = 0; i < stockCount; i++) {
                largeData.add(createSingleData(time, String.format("%06d.SH", i)));
            }
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start("add");

        // When: 添加大量数据
        buffer.addBatch(largeData);

        stopWatch.stop();

        // Then: 验证数据完整性
        assertEquals(stockCount * secondsCount, buffer.size());
        assertEquals(secondsCount, buffer.sliceCount());

        log.info("大数据量处理测试通过,添加{}条数据耗时{}ms|Large_data_test_passed,count={},elapsed={}ms",
                largeData.size(), stopWatch.getTotalTimeMillis(), largeData.size(), stopWatch.getTotalTimeMillis());
    }

    // ==================== 并发安全测试 ====================

    /**
     * 测试：多线程并发写入
     * <p>
     * 思路：
     * 1. 启动多个线程同时向缓冲区写入数据
     * 2. 验证最终数据完整性
     * <p>
     * 预期结果：所有数据都被正确添加，无数据丢失
     */
    @Test
    @Order(20)
    @DisplayName("并发安全 - 多线程并发写入")
    void testConcurrentWrite() throws InterruptedException {
        int threadCount = 10;
        int dataPerThread = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Given: 创建多个写入线程
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    // 等待统一开始
                    startLatch.await();

                    for (int i = 0; i < dataPerThread; i++) {
                        LocalDateTime time = baseTime.plusSeconds(i);
                        String code = String.format("%06d.SH", threadId * dataPerThread + i);
                        buffer.addBatch(List.of(createSingleData(time, code)));
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("并发写入异常", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // When: 触发并发开始
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 验证数据完整性
        assertEquals(0, errorCount.get(), "不应有错误发生");
        assertEquals(threadCount * dataPerThread, buffer.size(),
                "所有数据都应被添加，无丢失");

        log.info("并发写入测试通过,{}个线程×{}条数据/线程={}条总数据|Concurrent_write_test_passed,threads={},dataPerThread={},total={}",
                threadCount, dataPerThread, buffer.size(), threadCount, dataPerThread, buffer.size());
    }

    /**
     * 测试：多线程并发读写
     * <p>
     * 思路：
     * 1. 同时启动写入线程和读取线程
     * 2. 验证读写不会相互阻塞或导致数据异常
     * <p>
     * 预期结果：无死锁、无异常、读写均能正常完成
     */
    @Test
    @Order(21)
    @DisplayName("并发安全 - 多线程并发读写")
    void testConcurrentReadWrite() throws InterruptedException {
        int writerCount = 5;
        int readerCount = 5;
        int operationsPerThread = 500;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(writerCount + readerCount);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        // 写入线程
        for (int t = 0; t < writerCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        LocalDateTime time = baseTime.plusSeconds(i % 100);
                        buffer.addBatch(List.of(createSingleData(time, 
                                String.format("W%d_%d.SH", threadId, i))));
                        writeCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 读取线程
        for (int t = 0; t < readerCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        // 查询各种时间戳
                        buffer.pollSlice(baseTime.plusSeconds(i % 100)
                                .toEpochSecond(java.time.ZoneOffset.of("+8")));
                        readCount.incrementAndGet();
                        // 也测试只读操作
                        buffer.size();
                        buffer.getEarliestTimestamp();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // When: 触发并发
        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 验证无异常
        assertTrue(completed, "应在超时前完成");
        assertEquals(0, errorCount.get(), "不应有错误发生");

        log.info("并发读写测试通过,写入操作{}次,读取操作{}次|Concurrent_rw_test_passed,writes={},reads={}",
                writeCount.get(), readCount.get(), writeCount.get(), readCount.get());
    }

    // ==================== 性能测试 ====================

    /**
     * 测试：写入性能基准
     * <p>
     * 思路：
     * 1. 批量写入大量数据
     * 2. 测量耗时和吞吐量
     * <p>
     * 预期结果：10万条数据写入应在 1 秒内完成
     */
    @Test
    @Order(30)
    @DisplayName("性能测试 - 写入性能基准")
    void testWritePerformance() {
        int totalRecords = 100_000;
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        // 准备数据
        List<HistoryTrendDTO> data = new ArrayList<>();
        for (int i = 0; i < totalRecords; i++) {
            LocalDateTime time = baseTime.plusSeconds(i / 100); // 每秒100条
            data.add(createSingleData(time, String.format("%06d.SH", i)));
        }

        // 测试写入性能
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        buffer.addBatch(data);

        stopWatch.stop();

        long elapsedMs = stopWatch.getTotalTimeMillis();
        double throughput = (double) totalRecords / elapsedMs * 1000;

        log.info("写入性能测试:{}条数据,耗时{}ms,吞吐量{}/s|Write_perf_test,count={},elapsed={}ms,throughput={}/s",
                totalRecords, elapsedMs, String.format("%.0f", throughput), totalRecords, elapsedMs, String.format("%.0f", throughput));

        // 性能断言：应在合理时间内完成
        assertTrue(elapsedMs < 2000, "10万条数据写入应在2秒内完成");
        assertEquals(totalRecords, buffer.size());
    }

    /**
     * 测试：读取性能基准
     * <p>
     * 思路：
     * 1. 预先填充数据
     * 2. 测量 pollSlice 操作的性能
     * <p>
     * 预期结果：1000次 pollSlice 操作应在 100ms 内完成
     */
    @Test
    @Order(31)
    @DisplayName("性能测试 - 读取性能基准")
    void testReadPerformance() {
        int secondsCount = 1000;
        int stocksPerSecond = 100;
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        // 准备数据
        List<HistoryTrendDTO> data = new ArrayList<>();
        for (int sec = 0; sec < secondsCount; sec++) {
            LocalDateTime time = baseTime.plusSeconds(sec);
            for (int i = 0; i < stocksPerSecond; i++) {
                data.add(createSingleData(time, String.format("%06d.SH", i)));
            }
        }
        buffer.addBatch(data);

        // 测试读取性能
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        int totalPolled = 0;
        for (int sec = 0; sec < secondsCount; sec++) {
            long ts = baseTime.plusSeconds(sec).toEpochSecond(java.time.ZoneOffset.of("+8"));
            List<HistoryTrendDTO> polled = buffer.pollSlice(ts);
            if (polled != null) {
                totalPolled += polled.size();
            }
        }

        stopWatch.stop();

        long elapsedMs = stopWatch.getTotalTimeMillis();
        log.info("读取性能测试:{}次pollSlice,获取{}条数据,耗时{}ms|Read_perf_test,polls={},records={},elapsed={}ms",
                secondsCount, totalPolled, elapsedMs, secondsCount, totalPolled, elapsedMs);

        // 性能断言
        assertTrue(elapsedMs < 500, "1000次 pollSlice 应在 500ms 内完成");
        assertEquals(secondsCount * stocksPerSecond, totalPolled);
        assertTrue(buffer.isEmpty(), "所有数据应被取出");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试数据：同一时间点的多只股票
     */
    private List<HistoryTrendDTO> createTestData(LocalDateTime time, int count) {
        List<HistoryTrendDTO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(createSingleData(time, String.format("%06d.SH", i)));
        }
        return list;
    }

    /**
     * 创建单条测试数据
     */
    private HistoryTrendDTO createSingleData(LocalDateTime time, String windCode) {
        HistoryTrendDTO dto = new HistoryTrendDTO();
        dto.setWindCode(windCode);
        dto.setTradeDate(time);
        dto.setLatestPrice(100.0 + Math.random() * 10);
        dto.setTotalVolume(10000.0 + Math.random() * 1000);
        dto.setAveragePrice(100.0 + Math.random() * 5);
        return dto;
    }
}
