package com.hao.strategyengine.integration.feign;

import com.hao.strategyengine.common.model.vo.datacollector.StockBasicInfoQueryResultVO;
import dto.HistoryTrendDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2025-08-24 19:01:50
 * @description: 数据采集服务远程调用客户端
 */
@FeignClient(value = "quant-data-collector")
public interface DataCollectorClient {
    String BASE_URL = "/data-collector";

    @GetMapping(BASE_URL + "/base_date/get_trade_date")
    List<String> getTradeDateListByTime(@RequestParam String startTime, @RequestParam String endTime);

    @GetMapping(BASE_URL + "/base_date/stock_basic_list")
    List<StockBasicInfoQueryResultVO> queryStockBasicInfo(
            @Parameter(description = "股票代码", example = "000001.SZ")
            @RequestParam(required = false) String windCode,
            @Parameter(description = "股票名称", example = "平安银行")
            @RequestParam(required = false) String windName,
            @Parameter(description = "申万行业代码", example = "801780")
            @RequestParam(required = false) String swIndustryCode,
            @Parameter(description = "申万行业名称", example = "银行")
            @RequestParam(required = false) String swIndustryName,
            @Parameter(description = "中信行业代码", example = "CI005001")
            @RequestParam(required = false) String citicIndustryCode,
            @Parameter(description = "中信行业名称", example = "银行")
            @RequestParam(required = false) String citicIndustryName,
            @Parameter(description = "上市日期开始", example = "2020-01-01")
            @RequestParam(required = false) String listingDateStart,
            @Parameter(description = "上市日期结束", example = "2023-12-31")
            @RequestParam(required = false) String listingDateEnd,
            @Parameter(description = "股票状态", example = "L")
            @RequestParam(required = false) String statusExistence,
            @Parameter(description = "概念板块", example = "新能源")
            @RequestParam(required = false) String conceptPlates,
            @Parameter(description = "热门概念", example = "人工智能")
            @RequestParam(required = false) String hotConcepts,
            @Parameter(description = "产业链", example = "新能源汽车")
            @RequestParam(required = false) String industryChain,
            @Parameter(description = "是否长期跌破净资产", example = "N")
            @RequestParam(required = false) String isLongBelowNetAsset,
            @Parameter(description = "公司简介关键词")
            @RequestParam(required = false) String companyProfile,
            @Parameter(description = "经营范围关键词")
            @RequestParam(required = false) String businessScope,
            @Parameter(description = "总股本最小值")
            @RequestParam(required = false) Long totalSharesMin,
            @Parameter(description = "总股本最大值")
            @RequestParam(required = false) Long totalSharesMax,
            @Parameter(description = "流通股本最小值")
            @RequestParam(required = false) Long floatSharesMin,
            @Parameter(description = "流通股本最大值")
            @RequestParam(required = false) Long floatSharesMax,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNo,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize);

    @Operation(summary = "获取指定股票列表当日分时数据", description = "根据交易日获取指定股票列表当日分时数据")
    @GetMapping(BASE_URL + "/quotation/get_date_trend")
    List<HistoryTrendDTO> getHistoryTrendDataByStockList(
            @Parameter(description = "起始日期，格式yyyyMMdd", required = true)
            @RequestParam String startDate,
            @Parameter(description = "结束日期，格式yyyyMMdd", required = true)
            @RequestParam String endDate,
            @Parameter(description = "股票列表", required = true)
            @RequestParam List<String> stockList);
}
