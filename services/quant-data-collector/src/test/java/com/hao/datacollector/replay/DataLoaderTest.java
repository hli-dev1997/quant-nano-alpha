package com.hao.datacollector.replay;

import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataLoader 数据预加载器测试类
 * <p>
 * 测试目标：验证 DataLoader 正确调用 QuotationService 并处理返回数据
 * <p>
 * 测试范围：
 * 1. 功能测试：加载时间片数据、加载全天数据
 * 2. 边界测试：空数据、异常处理
 * 3. 配置测试：股票代码列表解析
 * <p>
 * 注意：使用 Mock QuotationService 隔离数据库依赖
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataLoaderTest {

    @Mock
    private QuotationService quotationService;

    private ReplayProperties config;
    private DataLoader dataLoader;

    @BeforeEach
    void setUp() {
        config = new ReplayProperties();
        config.setStartDate("20250601");
        config.setEndDate("20250601");
        dataLoader = new DataLoader(quotationService, config);
    }

    // ==================== 基本功能测试 ====================

    /**
     * 测试：加载时间片数据
     * <p>
     * 思路：
     * 1. Mock QuotationService 返回测试数据
     * 2. 调用 loadTimeSlice
     * 3. 验证正确调用 Service 并返回数据
     * <p>
     * 预期结果：返回 Mock 的数据列表
     */
    @Test
    @Order(1)
    @DisplayName("基本功能 - 加载时间片数据")
    void testLoadTimeSlice() {
        // Given: Mock 返回测试数据
        List<HistoryTrendDTO> mockData = createTestData(100);
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        LocalDateTime start = LocalDateTime.of(2025, 6, 1, 9, 30, 0);
        LocalDateTime end = LocalDateTime.of(2025, 6, 1, 9, 35, 0);

        // When: 加载数据
        List<HistoryTrendDTO> result = dataLoader.loadTimeSlice(start, end);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(100, result.size());

        // 验证 Service 被正确调用
        verify(quotationService).getHistoryTrendDataByStockList(
                eq("20250601"),  // startDate
                eq("20250601"),  // endDate
                eq(Collections.emptyList())  // 空股票列表（全市场）
        );

        log.info("加载时间片数据测试通过");
    }

    /**
     * 测试：加载全天数据
     * <p>
     * 思路：测试 loadFullDay 方法
     * <p>
     * 预期结果：正确调用 Service 并返回数据
     */
    @Test
    @Order(2)
    @DisplayName("基本功能 - 加载全天数据")
    void testLoadFullDay() {
        // Given
        List<HistoryTrendDTO> mockData = createTestData(5000);
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        // When
        List<HistoryTrendDTO> result = dataLoader.loadFullDay("20250601");

        // Then
        assertNotNull(result);
        assertEquals(5000, result.size());

        verify(quotationService).getHistoryTrendDataByStockList(
                eq("20250601"),
                eq("20250601"),
                eq(Collections.emptyList())
        );

        log.info("加载全天数据测试通过");
    }

    // ==================== 股票代码配置测试 ====================

    /**
     * 测试：指定股票代码列表
     * <p>
     * 思路：
     * 1. 配置 stockCodes
     * 2. 验证 Service 收到正确的股票列表
     * <p>
     * 预期结果：股票代码被正确解析并传递
     */
    @Test
    @Order(10)
    @DisplayName("配置测试 - 指定股票代码")
    void testWithStockCodes() {
        // Given: 配置指定股票代码
        config.setStockCodes("600519.SH,000001.SZ,600036.SH");

        List<HistoryTrendDTO> mockData = createTestData(30);
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(mockData);

        // When
        dataLoader.loadFullDay("20250601");

        // Then: 验证股票列表被正确解析
        verify(quotationService).getHistoryTrendDataByStockList(
                eq("20250601"),
                eq("20250601"),
                argThat(list -> {
                    // 验证列表包含3只股票
                    return list.size() == 3
                            && list.contains("600519.SH")
                            && list.contains("000001.SZ")
                            && list.contains("600036.SH");
                })
        );

        log.info("指定股票代码测试通过");
    }

    /**
     * 测试：空股票代码配置
     * <p>
     * 思路：stockCodes 为空时应查询全市场
     * <p>
     * 预期结果：传递空列表给 Service
     */
    @Test
    @Order(11)
    @DisplayName("配置测试 - 空股票代码（全市场）")
    void testEmptyStockCodes() {
        // Given: 不配置股票代码
        config.setStockCodes(null);

        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        dataLoader.loadFullDay("20250601");

        // Then: 应传递空列表
        verify(quotationService).getHistoryTrendDataByStockList(
                anyString(),
                anyString(),
                eq(Collections.emptyList())
        );

        log.info("空股票代码（全市场）测试通过");
    }

    /**
     * 测试：单只股票配置
     * <p>
     * 思路：只配置一只股票时的解析
     * <p>
     * 预期结果：列表只包含一只股票
     */
    @Test
    @Order(12)
    @DisplayName("配置测试 - 单只股票")
    void testSingleStockCode() {
        // Given
        config.setStockCodes("600519.SH");

        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        dataLoader.loadFullDay("20250601");

        // Then
        verify(quotationService).getHistoryTrendDataByStockList(
                anyString(),
                anyString(),
                argThat(list -> list.size() == 1 && list.contains("600519.SH"))
        );

        log.info("单只股票配置测试通过");
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试：Service 返回空列表
     * <p>
     * 思路：验证返回空数据时不会抛异常
     * <p>
     * 预期结果：正常返回空列表
     */
    @Test
    @Order(20)
    @DisplayName("边界条件 - 空数据返回")
    void testEmptyDataReturn() {
        // Given
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        // When
        List<HistoryTrendDTO> result = dataLoader.loadFullDay("20250601");

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        log.info("空数据返回测试通过");
    }

    /**
     * 测试：Service 返回 null
     * <p>
     * 思路：如果 Service 返回 null，DataLoader 应能处理
     * <p>
     * 预期结果：不抛异常，返回 null
     */
    @Test
    @Order(21)
    @DisplayName("边界条件 - null 数据返回")
    void testNullDataReturn() {
        // Given
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(null);

        // When
        List<HistoryTrendDTO> result = dataLoader.loadFullDay("20250602");

        // Then
        // 注意：DataLoader 实现中如果返回 null，会转为空列表，这里根据实际实现调整断言
        // 假设 DataLoader 做了 null safety
        assertNotNull(result);
        assertTrue(result.isEmpty());

        log.info("null 数据返回测试通过");
    }

    /**
     * 测试：Service 抛出异常
     * <p>
     * 思路：验证异常能正常向上传播
     * <p>
     * 预期结果：异常被抛出
     */
    @Test
    @Order(22)
    @DisplayName("边界条件 - Service 异常")
    void testServiceException() {
        // Given
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            dataLoader.loadFullDay("20250601");
        });

        log.info("Service 异常测试通过");
    }

    // ==================== 日期格式测试 ====================

    /**
     * 测试：日期格式转换
     * <p>
     * 思路：验证 LocalDateTime 被正确格式化为 yyyyMMdd 字符串
     * <p>
     * 预期结果：日期格式正确
     */
    @Test
    @Order(30)
    @DisplayName("日期格式 - LocalDateTime 转换")
    void testDateFormatConversion() {
        // Given
        when(quotationService.getHistoryTrendDataByStockList(anyString(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());

        LocalDateTime start = LocalDateTime.of(2025, 12, 31, 9, 30, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 31, 15, 0, 0);

        // When
        dataLoader.loadTimeSlice(start, end);

        // Then: 验证日期格式为 yyyyMMdd
        verify(quotationService).getHistoryTrendDataByStockList(
                eq("20251231"),
                eq("20251231"),
                anyList()
        );

        log.info("日期格式转换测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试数据
     */
    private List<HistoryTrendDTO> createTestData(int count) {
        List<HistoryTrendDTO> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HistoryTrendDTO dto = new HistoryTrendDTO();
            dto.setWindCode(String.format("%06d.SH", i));
            dto.setTradeDate(LocalDateTime.now());
            dto.setLatestPrice(100.0);
            dto.setTotalVolume(10000.0);
            dto.setAveragePrice(100.0);
            list.add(dto);
        }
        return list;
    }
}
