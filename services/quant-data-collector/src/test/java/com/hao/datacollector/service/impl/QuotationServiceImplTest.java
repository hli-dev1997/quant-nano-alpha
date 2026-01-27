package com.hao.datacollector.service.impl;

import com.hao.datacollector.dto.quotation.DailyHighLowDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuotationServiceImpl 集成测试
 *
 * <p><b>测试目的：</b></p>
 * <ol>
 *     <li>连接真实数据库，验证 {@code getHistoryTrendDataByStockList} 方法的冷热数据查询逻辑。</li>
 *     <li>使用真实股票代码（贵州茅台 600519.SH）验证跨表查询是否能正确返回数据。</li>
 *     <li>验证并行查询后的结果合并与排序是否正确。</li>
 * </ol>
 *
 * @author hli
 * @date 2025-12-27
 */
@Slf4j
@SpringBootTest
class QuotationServiceImplTest {

    @Autowired
    private QuotationService quotationService;

    private static final String STOCK_CODE = "600519.SH"; // 贵州茅台

    /**
     * 测试场景：查询范围仅涉及热数据表 (2024年及以后)
     * 预期：应从 tb_quotation_history_hot 返回数据
     */
    @Test
    @DisplayName("集成测试_查询热数据_2024年")
    void testGetHistoryTrend_OnlyHotData() {
        String startDate = "20240104"; // 2024年首个交易日附近
        String endDate = "20240105";
        List<String> stockList = Collections.singletonList(STOCK_CODE);

        log.info("开始测试：仅查询热数据，范围 {} 至 {}", startDate, endDate);
        List<HistoryTrendDTO> result = quotationService.getHistoryTrendDataByStockList(startDate, endDate, stockList);

        assertNotNull(result, "结果列表不应为 null");
        assertFalse(result.isEmpty(), "结果列表不应为空，应包含 2024 年的真实行情数据");
        
        // 验证数据归属
        result.forEach(dto -> {
            assertEquals(STOCK_CODE, dto.getWindCode(), "股票代码应匹配");
            assertTrue(dto.getTradeDate().getYear() >= 2024, "交易年份应在 2024 年及以后");
        });
        
        log.info("测试通过：成功获取 {} 条热数据", result.size());
    }

    /**
     * 测试场景：查询范围仅涉及温数据表 (2024年以前)
     * 预期：应从 tb_quotation_history_warm 返回数据
     */
    @Test
    @DisplayName("集成测试_查询温数据_2023年")
    void testGetHistoryTrend_OnlyWarmData() {
        String startDate = "20231228"; // 2023年年末
        String endDate = "20231229";
        List<String> stockList = Collections.singletonList(STOCK_CODE);

        log.info("开始测试：仅查询温数据，范围 {} 至 {}", startDate, endDate);
        List<HistoryTrendDTO> result = quotationService.getHistoryTrendDataByStockList(startDate, endDate, stockList);

        assertNotNull(result, "结果列表不应为 null");
        assertFalse(result.isEmpty(), "结果列表不应为空，应包含 2023 年的真实行情数据");

        // 验证数据归属
        result.forEach(dto -> {
            assertEquals(STOCK_CODE, dto.getWindCode(), "股票代码应匹配");
            assertTrue(dto.getTradeDate().getYear() < 2024, "交易年份应在 2024 年以前");
        });

        log.info("测试通过：成功获取 {} 条温数据", result.size());
    }

    /**
     * 测试场景：查询范围跨越冷热数据表 (2023年末 - 2024年初)
     * 预期：应同时从两张表返回数据并合并，且结果有序
     */
    @Test
    @DisplayName("集成测试_跨冷热表并行查询_2023跨2024")
    void testGetHistoryTrend_AcrossHotAndWarmData_Parallel() {
        String startDate = "20231228";
        String endDate = "20240105";
        List<String> stockList = Collections.singletonList(STOCK_CODE);

        log.info("开始测试：跨冷热表并行查询，范围 {} 至 {}", startDate, endDate);
        List<HistoryTrendDTO> result = quotationService.getHistoryTrendDataByStockList(startDate, endDate, stockList);

        assertNotNull(result, "结果列表不应为 null");
        assertFalse(result.isEmpty(), "结果列表不应为空，应包含跨年的真实行情数据");

        // 验证是否包含两部分数据
        boolean hasWarmData = result.stream().anyMatch(dto -> dto.getTradeDate().getYear() < 2024);
        boolean hasHotData = result.stream().anyMatch(dto -> dto.getTradeDate().getYear() >= 2024);

        assertTrue(hasWarmData, "结果中应包含 2024 年以前的温数据");
        assertTrue(hasHotData, "结果中应包含 2024 年及以后的热数据");

        // 验证数据是否按时间升序排列
        List<HistoryTrendDTO> sortedList = new ArrayList<>(result);
        sortedList.sort(Comparator.comparing(HistoryTrendDTO::getTradeDate));
        assertEquals(sortedList, result, "并行查询合并后的结果应保持时间升序");

        log.info("测试通过：成功获取 {} 条跨表数据，且排序正确", result.size());
    }

    /**
     * 测试场景：查询指定股票列表在2026年1月5日的当日最高价和最低价
     * 预期：应返回每只股票的最高价和最低价对应的完整分时数据
     */
    @Test
    @DisplayName("集成测试_查询当日最高最低价_601606和600519_20260105")
    void testGetDailyHighLowByStockList_20260105() {
        String startDate = "20260105";
        String endDate = "20260105";
        List<String> stockList = Arrays.asList("601606.SH", "600519.SH");

        log.info("开始测试：查询当日最高最低价，日期 {}，股票列表 {}", startDate, stockList);
        Map<String, DailyHighLowDTO> result = quotationService.getDailyHighLowByStockList(startDate, endDate, stockList);

        assertNotNull(result, "结果 Map 不应为 null");
        assertFalse(result.isEmpty(), "结果 Map 不应为空");

        // 验证每只股票的结果
        for (String windCode : stockList) {
            if (result.containsKey(windCode)) {
                DailyHighLowDTO dailyHighLow = result.get(windCode);
                assertNotNull(dailyHighLow, windCode + " 的 DailyHighLowDTO 不应为 null");
                
                HistoryTrendDTO highData = dailyHighLow.getHighPriceData();
                HistoryTrendDTO lowData = dailyHighLow.getLowPriceData();
                
                assertNotNull(highData, windCode + " 的最高价数据不应为 null");
                assertNotNull(lowData, windCode + " 的最低价数据不应为 null");
                
                // 验证股票代码一致性
                assertEquals(windCode, highData.getWindCode(), "最高价数据的股票代码应匹配");
                assertEquals(windCode, lowData.getWindCode(), "最低价数据的股票代码应匹配");
                
                // 验证最高价 >= 最低价
                assertTrue(highData.getLatestPrice() >= lowData.getLatestPrice(),
                        windCode + " 的最高价应大于等于最低价");
                
                log.info("股票 {} 最高价: {} (时间: {}), 最低价: {} (时间: {})",
                        windCode,
                        highData.getLatestPrice(), highData.getTradeDate(),
                        lowData.getLatestPrice(), lowData.getTradeDate());
            } else {
                log.warn("股票 {} 在数据库中没有 {} 的分时数据", windCode, startDate);
            }
        }

        log.info("测试通过：成功获取 {} 只股票的当日最高最低价", result.size());
    }
}

