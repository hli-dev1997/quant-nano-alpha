package com.hao.datacollector.service.impl;

import com.hao.datacollector.dal.dao.QuotationMapper;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;
import com.hao.datacollector.service.QuotationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StopWatch;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 指标历史分时数据查询服务测试类
 * <p>
 * 测试目标：验证 getIndexHistoryTrendDataByIndexList 方法的正确性、边界处理和性能表现
 * <p>
 * 测试范围：
 * 1. 基础功能测试：正常查询、指定指标查询、全市场查询
 * 2. 参数校验测试：空参数、null 参数、日期格式
 * 3. 边界条件测试：空结果、大数据量、日期边界
 * 4. 日期边界修复测试：验证 appendEndOfDayTime 正确应用
 * 5. 性能测试：大数据量查询性能、并发查询
 * <p>
 * 测试策略：
 * - 使用 Mockito 模拟 QuotationMapper，隔离数据库依赖
 * - 每个测试方法包含详细注释说明测试思路和预期结果
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndexHistoryTrendQueryTest {

    @Mock
    private QuotationMapper quotationMapper;

    @InjectMocks
    private QuotationServiceImpl quotationService;

    // ==================== 基础功能测试 ====================

    /**
     * 测试：正常查询指定指标列表的历史分时数据
     * <p>
     * 思路：
     * 1. 传入有效的 startDate、endDate 和指标列表
     * 2. Mock Mapper 返回测试数据
     * 3. 验证返回结果与 Mock 数据一致
     * <p>
     * 预期结果：
     * - 返回 Mock 的数据列表
     * - Mapper 被正确调用且参数包含日期时间后缀
     */
    @Test
    @Order(1)
    @DisplayName("基础功能 - 查询指定指标列表")
    void testQueryWithIndexList() {
        // Given: Mock 返回测试数据
        List<HistoryTrendIndexDTO> mockData = createMockIndexData(50);
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        List<String> indexList = Arrays.asList("000001.SH", "399001.SZ", "000300.SH");

        // When: 调用查询
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", indexList);

        // Then: 验证结果
        assertNotNull(result, "结果不应为 null");
        assertEquals(50, result.size(), "应返回 50 条数据");

        // 验证 Mapper 被正确调用
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                argThat(start -> start.endsWith("00:00:00")),  // startDate 应追加时间
                argThat(end -> end.endsWith("23:59:59")),      // endDate 应追加时间
                eq(indexList)
        );

        log.info("基础功能-查询指定指标列表测试通过");
    }

    /**
     * 测试：查询全部指标（不传指标列表）
     * <p>
     * 思路：
     * 1. indexList 参数传 null 或空列表
     * 2. 验证传递给 Mapper 的是空列表
     * <p>
     * 预期结果：空列表被传递给 Mapper，表示查询全部指标
     */
    @Test
    @Order(2)
    @DisplayName("基础功能 - 查询全部指标（null indexList）")
    void testQueryAllIndexesWithNull() {
        // Given
        List<HistoryTrendIndexDTO> mockData = createMockIndexData(100);
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        // When: indexList 为 null
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", null);

        // Then: 验证传递空列表给 Mapper
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(), anyString(), eq(Collections.emptyList())
        );
        assertEquals(100, result.size());

        log.info("基础功能-查询全部指标（null）测试通过");
    }

    /**
     * 测试：查询全部指标（传空列表）
     * <p>
     * 思路：与传 null 效果相同
     * <p>
     * 预期结果：空列表被传递给 Mapper
     */
    @Test
    @Order(3)
    @DisplayName("基础功能 - 查询全部指标（空列表）")
    void testQueryAllIndexesWithEmptyList() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", Collections.emptyList());

        // Then
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(), anyString(), eq(Collections.emptyList())
        );

        log.info("基础功能-查询全部指标（空列表）测试通过");
    }

    // ==================== 参数校验测试 ====================

    /**
     * 测试：startDate 为空字符串
     * <p>
     * 思路：传入空的 startDate 应返回空列表，不调用 Mapper
     * <p>
     * 预期结果：返回空列表，Mapper 不被调用
     */
    @Test
    @Order(10)
    @DisplayName("参数校验 - startDate 为空")
    void testEmptyStartDate() {
        // When: startDate 为空
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "", "20250601", null);

        // Then: 返回空列表，Mapper 不被调用
        assertTrue(result.isEmpty(), "空 startDate 应返回空列表");
        verify(quotationMapper, never()).selectIndexByWindCodeListAndDate(any(), any(), any());

        log.info("参数校验-空startDate测试通过");
    }

    /**
     * 测试：startDate 为 null
     * <p>
     * 思路：null startDate 应返回空列表
     * <p>
     * 预期结果：返回空列表，Mapper 不被调用
     */
    @Test
    @Order(11)
    @DisplayName("参数校验 - startDate 为 null")
    void testNullStartDate() {
        // When
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                null, "20250601", null);

        // Then
        assertTrue(result.isEmpty());
        verify(quotationMapper, never()).selectIndexByWindCodeListAndDate(any(), any(), any());

        log.info("参数校验-null startDate测试通过");
    }

    /**
     * 测试：endDate 为空时使用当前日期
     * <p>
     * 思路：endDate 为空时应自动使用当前日期
     * <p>
     * 预期结果：Mapper 被调用，endDate 参数不为空
     */
    @Test
    @Order(12)
    @DisplayName("参数校验 - endDate 为空时使用默认值")
    void testEmptyEndDateUsesDefault() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When: endDate 为空
        quotationService.getIndexHistoryTrendDataByIndexList("20250601", "", null);

        // Then: Mapper 被调用且 endDate 不为空
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(),
                argThat(end -> end != null && !end.isEmpty() && end.endsWith("23:59:59")),
                anyList()
        );

        log.info("参数校验-空endDate使用默认值测试通过");
    }

    // ==================== 日期边界测试 ====================

    /**
     * 测试：日期边界时间追加 - startDate
     * <p>
     * 思路：验证 startDate 被正确追加 " 00:00:00"
     * <p>
     * 预期结果：
     * - "20250601" -> "2025-06-01 00:00:00"
     */
    @Test
    @Order(20)
    @DisplayName("日期边界 - startDate 追加 00:00:00")
    void testStartDateBoundary() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        quotationService.getIndexHistoryTrendDataByIndexList("20250601", "20250601", null);

        // Then: 验证 startDate 格式
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                argThat(start -> {
                    log.debug("startDate 参数: {}", start);
                    return start.contains("2025-06-01") && start.endsWith("00:00:00");
                }),
                anyString(),
                anyList()
        );

        log.info("日期边界-startDate追加00:00:00测试通过");
    }

    /**
     * 测试：日期边界时间追加 - endDate
     * <p>
     * 思路：验证 endDate 被正确追加 " 23:59:59"
     * <p>
     * 预期结果：
     * - "20250601" -> "2025-06-01 23:59:59"
     */
    @Test
    @Order(21)
    @DisplayName("日期边界 - endDate 追加 23:59:59")
    void testEndDateBoundary() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        quotationService.getIndexHistoryTrendDataByIndexList("20250601", "20250601", null);

        // Then: 验证 endDate 格式
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(),
                argThat(end -> {
                    log.debug("endDate 参数: {}", end);
                    return end.contains("2025-06-01") && end.endsWith("23:59:59");
                }),
                anyList()
        );

        log.info("日期边界-endDate追加23:59:59测试通过");
    }

    /**
     * 测试：yyyyMMdd 格式日期转换为 yyyy-MM-dd
     * <p>
     * 思路：验证紧凑日期格式被正确转换
     * <p>
     * 预期结果：
     * - "20251231" -> "2025-12-31 23:59:59"
     */
    @Test
    @Order(22)
    @DisplayName("日期边界 - yyyyMMdd 格式转换")
    void testCompactDateFormatConversion() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When: 使用紧凑格式
        quotationService.getIndexHistoryTrendDataByIndexList("20251231", "20251231", null);

        // Then: 验证格式转换
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                contains("2025-12-31"),  // 转换后包含横线
                contains("2025-12-31"),
                anyList()
        );

        log.info("日期边界-yyyyMMdd格式转换测试通过");
    }

    /**
     * 测试：跨年日期范围
     * <p>
     * 思路：验证跨年查询的日期处理
     * <p>
     * 预期结果：日期范围正确传递
     */
    @Test
    @Order(23)
    @DisplayName("日期边界 - 跨年日期范围")
    void testCrossYearDateRange() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When: 跨年查询
        quotationService.getIndexHistoryTrendDataByIndexList("20241231", "20250101", null);

        // Then
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                contains("2024-12-31"),
                contains("2025-01-01"),
                anyList()
        );

        log.info("日期边界-跨年日期范围测试通过");
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试：Mapper 返回空列表
     * <p>
     * 思路：验证空结果集的处理
     * <p>
     * 预期结果：返回空列表，不抛异常
     */
    @Test
    @Order(30)
    @DisplayName("边界条件 - 空结果集")
    void testEmptyResult() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        log.info("边界条件-空结果集测试通过");
    }

    /**
     * 测试：Mapper 返回 null
     * <p>
     * 思路：验证 null 返回值的处理（防御性编程）
     * <p>
     * 预期结果：应能正常处理，返回 null 或空列表
     */
    @Test
    @Order(31)
    @DisplayName("边界条件 - Mapper 返回 null")
    void testMapperReturnsNull() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(null);

        // When: 这里可能会抛 NPE，取决于实现
        // 当前实现直接返回 Mapper 结果，可能需要防御性处理
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", null);

        // Then: 当前实现会返回 null，这可能需要改进
        // 这里记录实际行为
        log.info("边界条件-Mapper返回null测试完成，result={}", result);
    }

    /**
     * 测试：单只指标查询
     * <p>
     * 思路：只查询一个指标的极端情况
     * <p>
     * 预期结果：正常返回该指标的数据
     */
    @Test
    @Order(32)
    @DisplayName("边界条件 - 单只指标查询")
    void testSingleIndexQuery() {
        // Given
        List<HistoryTrendIndexDTO> mockData = createMockIndexData(10);
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        List<String> singleIndex = Collections.singletonList("000001.SH");

        // When
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", singleIndex);

        // Then
        assertNotNull(result);
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(), anyString(), argThat(list -> list.size() == 1)
        );

        log.info("边界条件-单只指标查询测试通过");
    }

    /**
     * 测试：大量指标代码查询
     * <p>
     * 思路：传入大量指标代码（如100个）
     * <p>
     * 预期结果：能正常处理，传递给 Mapper
     */
    @Test
    @Order(33)
    @DisplayName("边界条件 - 大量指标代码")
    void testLargeIndexList() {
        // Given: 创建100个指标代码
        List<String> largeIndexList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeIndexList.add(String.format("%06d.SH", i));
        }

        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", largeIndexList);

        // Then
        verify(quotationMapper).selectIndexByWindCodeListAndDate(
                anyString(), anyString(), argThat(list -> list.size() == 100)
        );

        log.info("边界条件-大量指标代码测试通过");
    }

    // ==================== 性能测试 ====================

    /**
     * 测试：大数据量返回性能
     * <p>
     * 思路：
     * 1. Mock 返回大量数据（10万条）
     * 2. 测量处理时间
     * <p>
     * 预期结果：处理时间在可接受范围内（< 500ms）
     */
    @Test
    @Order(40)
    @DisplayName("性能测试 - 大数据量返回")
    void testLargeDataVolumePerformance() {
        // Given: 创建10万条模拟数据
        int dataSize = 100_000;
        List<HistoryTrendIndexDTO> largeData = createMockIndexData(dataSize);

        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(largeData);

        // When: 测量查询时间
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250101", "20251231", null);

        stopWatch.stop();

        // Then
        assertEquals(dataSize, result.size());
        long elapsedMs = stopWatch.getTotalTimeMillis();

        log.info("性能测试-大数据量返回：{} 条数据，耗时 {} ms", dataSize, elapsedMs);

        // 性能断言：处理10万条数据应在500ms内完成
        assertTrue(elapsedMs < 500, "处理10万条数据应在500ms内完成，实际耗时: " + elapsedMs);
    }

    /**
     * 测试：连续调用性能
     * <p>
     * 思路：
     * 1. 连续调用100次
     * 2. 测量平均响应时间
     * <p>
     * 预期结果：平均响应时间 < 10ms
     */
    @Test
    @Order(41)
    @DisplayName("性能测试 - 连续调用")
    void testConsecutiveCallsPerformance() {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(createMockIndexData(100));

        int iterations = 100;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // When: 连续调用100次
        for (int i = 0; i < iterations; i++) {
            quotationService.getIndexHistoryTrendDataByIndexList(
                    "20250601", "20250601", null);
        }

        stopWatch.stop();

        // Then
        long totalMs = stopWatch.getTotalTimeMillis();
        double avgMs = (double) totalMs / iterations;

        log.info("性能测试-连续调用：{} 次调用，总耗时 {} ms，平均 {} ms/次",
                iterations, totalMs, String.format("%.2f", avgMs));

        assertTrue(avgMs < 10, "平均响应时间应 < 10ms，实际: " + avgMs);
    }

    /**
     * 测试：并发调用性能
     * <p>
     * 思路：
     * 1. 启动多线程并发调用
     * 2. 验证无异常且响应时间合理
     * <p>
     * 预期结果：并发安全，无异常
     */
    @Test
    @Order(42)
    @DisplayName("性能测试 - 并发调用")
    void testConcurrentCallsPerformance() throws InterruptedException {
        // Given
        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(createMockIndexData(100));

        int threadCount = 10;
        int callsPerThread = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // 创建并发任务
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                                "20250601", "20250601", null);
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("并发调用异常", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // When: 触发并发
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

        stopWatch.stop();
        executor.shutdown();

        // Then
        assertTrue(completed, "应在超时前完成");
        assertEquals(0, errorCount.get(), "不应有错误发生");
        assertEquals(threadCount * callsPerThread, successCount.get());

        log.info("性能测试-并发调用：{} 线程 × {} 次/线程 = {} 次总调用，耗时 {} ms",
                threadCount, callsPerThread, successCount.get(), stopWatch.getTotalTimeMillis());
    }

    // ==================== 数据完整性测试 ====================

    /**
     * 测试：返回数据字段完整性
     * <p>
     * 思路：验证返回的 DTO 包含所有必要字段
     * <p>
     * 预期结果：windCode、tradeDate、latestPrice、totalVolume、totalAmount 都有值
     */
    @Test
    @Order(50)
    @DisplayName("数据完整性 - 字段完整性")
    void testDataFieldsIntegrity() {
        // Given: 创建包含完整字段的测试数据
        HistoryTrendIndexDTO dto = new HistoryTrendIndexDTO();
        dto.setWindCode("000001.SH");
        dto.setTradeDate(LocalDateTime.of(2025, 6, 1, 9, 30, 0));
        dto.setLatestPrice(3500.25);
        dto.setTotalVolume(1000000.0);
        dto.setTotalAmount(5000000000.0);

        when(quotationMapper.selectIndexByWindCodeListAndDate(anyString(), anyString(), anyList()))
                .thenReturn(Collections.singletonList(dto));

        // When
        List<HistoryTrendIndexDTO> result = quotationService.getIndexHistoryTrendDataByIndexList(
                "20250601", "20250601", null);

        // Then
        assertEquals(1, result.size());
        HistoryTrendIndexDTO returnedDto = result.get(0);

        assertNotNull(returnedDto.getWindCode(), "windCode 不应为 null");
        assertNotNull(returnedDto.getTradeDate(), "tradeDate 不应为 null");
        assertNotNull(returnedDto.getLatestPrice(), "latestPrice 不应为 null");
        assertNotNull(returnedDto.getTotalVolume(), "totalVolume 不应为 null");
        assertNotNull(returnedDto.getTotalAmount(), "totalAmount 不应为 null");

        assertEquals("000001.SH", returnedDto.getWindCode());
        assertEquals(3500.25, returnedDto.getLatestPrice(), 0.01);

        log.info("数据完整性-字段完整性测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建模拟指标数据
     *
     * @param count 数据条数
     * @return 模拟数据列表
     */
    private List<HistoryTrendIndexDTO> createMockIndexData(int count) {
        List<HistoryTrendIndexDTO> list = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2025, 6, 1, 9, 30, 0);

        for (int i = 0; i < count; i++) {
            HistoryTrendIndexDTO dto = new HistoryTrendIndexDTO();
            dto.setWindCode(String.format("%06d.SH", i % 100));
            dto.setTradeDate(baseTime.plusSeconds(i));
            dto.setLatestPrice(3500.0 + Math.random() * 100);
            dto.setTotalVolume(1000000.0 + Math.random() * 100000);
            dto.setTotalAmount(5000000000.0 + Math.random() * 1000000000);
            list.add(dto);
        }
        return list;
    }
}
