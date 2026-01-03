package com.hao.datacollector.service;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;

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

    /**
     * 根据时间区间获取指定指标列表的历史分时数据
     *
     * @param startDate  起始日期（格式 yyyyMMdd）
     * @param endDate    结束日期（格式 yyyyMMdd）
     * @param indexList  指标代码列表（为空时查询所有指标）
     * @return 指标历史分时数据
     */
    List<HistoryTrendIndexDTO> getIndexHistoryTrendDataByIndexList(String startDate, String endDate, List<String> indexList);

    /**
     * 获取指定时间区间内每只股票每日的收盘价（最后一条分时数据）
     * <p>
     * 专为策略预热优化，仅返回 windCode, tradeDate, latestPrice 字段。
     *
     * @param startDate 起始日期 (yyyyMMdd)
     * @param endDate   结束日期 (yyyyMMdd)
     * @param stockList 股票代码列表
     * @return 包含每日收盘价的历史数据列表
     */
    List<HistoryTrendDTO> getDailyClosePriceByStockList(String startDate, String endDate, List<String> stockList);
}
