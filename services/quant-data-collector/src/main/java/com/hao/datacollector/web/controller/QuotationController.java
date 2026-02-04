package com.hao.datacollector.web.controller;

import com.hao.datacollector.dto.quotation.DailyHighLowDTO;
import com.hao.datacollector.dto.quotation.DailyOhlcDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;
import com.hao.datacollector.service.QuotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @author hli
 * @program: datacollector
 * @Date 2025-07-04 17:42:14
 * @description: 行情相关controller
 */
@Tag(name = "行情模块", description = "新闻模块数据处理接口")
@Slf4j
@RestController
@RequestMapping("/quotation")
public class QuotationController {
    @Autowired
    private QuotationService quotationService;

    @Operation(summary = "转档股票行情数据", description = "转档股票基础行情数据")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "服务正常运行"),
            @ApiResponse(responseCode = "500", description = "服务异常")
    })
    @PostMapping("/transfer_base")
    public ResponseEntity<String> transferQuotationBaseByStock(@RequestParam(required = false) String windCode,
                                                               @RequestParam(required = false) String startDate,
                                                               @RequestParam(required = false) String endDate) {
        Boolean success = quotationService.transferQuotationBaseByStock(windCode, startDate, endDate);
        if (!success) {
            return ResponseEntity.badRequest().body("数据转档失败");
        }
        return ResponseEntity.ok("数据转档成功");
    }

    @Operation(summary = "转档股票历史分时数据", description = "转档股票历史分时数据")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "服务正常运行"),
            @ApiResponse(responseCode = "500", description = "服务异常")
    })
    @PostMapping("/transfer_history_trend")
    public ResponseEntity<String> transferQuotationHistoryTrend(
            @RequestParam int tradeDate,
            @RequestParam String windCodes,
            @RequestParam(required = false, defaultValue = "0") Integer dateType) {
        Boolean success = quotationService.transferQuotationHistoryTrend(tradeDate, windCodes, dateType);
        if (!success) {
            return ResponseEntity.badRequest().body("数据转档失败");
        }
        return ResponseEntity.ok("数据转档成功");
    }

    @Operation(summary = "股票历史分时", description = "根据时间区间获取股票历史分时数据")
    @GetMapping("/get_history_trend")
    public List<HistoryTrendDTO> getHistoryTrendDataByDate(
            @Parameter(description = "起始日期，格式yyyy-MM-dd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyy-MM-dd", required = false)
            @RequestParam(required = false) String endDate) {
        return quotationService.getHistoryTrendDataByDate(startDate, endDate);
    }

    @Operation(summary = "获取指定股票列表当日分时数据", description = "根据交易日获取指定股票列表当日分时数据")
    @GetMapping("/get_date_trend")
    public List<HistoryTrendDTO> getHistoryTrendDataByStockList(
            @Parameter(description = "起始日期，格式yyyyMMdd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyyMMdd", required = true)
            @RequestParam String endDate,
            @Parameter(description = "股票列表", required = true)
            @RequestParam List<String> stockList
    ) {
        return quotationService.getHistoryTrendDataByStockList(startDate, endDate, stockList);
    }

    @Operation(summary = "获取指定指标列表历史分时数据", description = "根据时间区间获取指定指标列表的历史分时数据")
    @GetMapping("/get_index_trend")
    public List<HistoryTrendIndexDTO> getIndexHistoryTrendDataByIndexList(
            @Parameter(description = "起始日期，格式yyyyMMdd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyyMMdd", required = true)
            @RequestParam String endDate,
            @Parameter(description = "指标代码列表（为空时查询所有指标）", required = false)
            @RequestParam(required = false) List<String> indexList
    ) {
        return quotationService.getIndexHistoryTrendDataByIndexList(startDate, endDate, indexList);
    }

    @Operation(summary = "获取指定股票列表的当日最高价和最低价", description = "根据时间区间获取指定股票列表的当日最高价和最低价分时数据")
    @GetMapping("/get_daily_high_low")
    public Map<String, DailyHighLowDTO> getDailyHighLowByStockList(
            @Parameter(description = "起始日期，格式yyyyMMdd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyyMMdd", required = true)
            @RequestParam String endDate,
            @Parameter(description = "股票列表", required = true)
            @RequestParam List<String> stockList
    ) {
        return quotationService.getDailyHighLowByStockList(startDate, endDate, stockList);
    }

    @Operation(summary = "获取指定股票列表在多个日期的收盘价", description = "根据日期列表获取指定股票列表每天的收盘价（最后一条分时数据）")
    @GetMapping("/get_daily_close_by_dates")
    public Map<String, Map<String, HistoryTrendDTO>> getDailyClosePriceByDateList(
            @Parameter(description = "股票代码列表", required = true)
            @RequestParam List<String> stockList,
            @Parameter(description = "日期列表，格式yyyyMMdd", required = true)
            @RequestParam List<String> dateList
    ) {
        return quotationService.getDailyClosePriceByDateList(stockList, dateList);
    }

    @Operation(summary = "获取指定股票列表的每日OHLC数据",
            description = "根据时间区间获取每只股票每日的最高价、最低价、收盘价（使用 TableRouter + ParallelQueryExecutor 跨表查询）")
    @GetMapping("/get_daily_ohlc")
    public Map<String, Map<String, DailyOhlcDTO>> getDailyOhlcByStockList(
            @Parameter(description = "起始日期，格式yyyyMMdd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyyMMdd", required = true)
            @RequestParam String endDate,
            @Parameter(description = "股票列表", required = true)
            @RequestParam List<String> stockList
    ) {
        return quotationService.getDailyOhlcByStockList(startDate, endDate, stockList);
    }
}
