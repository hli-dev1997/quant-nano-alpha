package com.hao.datacollector.service;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;

import java.util.List;

/**
 * @author hli
 * @program: datacollector
 * @Date 2025-06-20 17:09:27
 * @description: 行情service
 */
public interface QuotationService {
    /**
     * 获取基础行情数据
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 当前股票基础行情数据
     */
    Boolean transferQuotationBaseByStock(String windCode, String startDate, String endDate);


    /**
     * 转档股票历史分时数据
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 股票代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    Boolean transferQuotationHistoryTrend(int tradeDate, String windCodes, Integer dateType);


    /**
     * 转档指标历史分时数据
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 指标代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    Boolean transferQuotationIndexHistoryTrend(int tradeDate, String windCodes, Integer dateType);

    /**
     * 根据时间区间获取A股历史分时数据
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 历史分时数据
     */
    List<HistoryTrendDTO> getHistoryTrendDataByDate(String startDate, String endDate);

    /**
     * 根据时间区间获取指定股票列表的A股历史分时数据
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param stockList 股票列表
     * @return 历史分时数据
     */
    List<HistoryTrendDTO> getHistoryTrendDataByStockList(String startDate, String endDate, List<String> stockList);
}
