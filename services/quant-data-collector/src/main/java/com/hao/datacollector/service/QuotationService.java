package com.hao.datacollector.service;

import com.hao.datacollector.dto.quotation.DailyHighLowDTO;
import com.hao.datacollector.dto.quotation.DailyOhlcDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;

import java.util.List;
import java.util.Map;

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
     * 根据精确时间区间获取指定股票列表的历史分时数据（回放专用）
     *
     * @param startTime 起始时间（格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（格式 yyyy-MM-dd HH:mm:ss）
     * @param stockList 股票列表
     * @return 历史分时数据
     */
    List<HistoryTrendDTO> getHistoryTrendDataByTimeRange(String startTime, String endTime, List<String> stockList);

    /**
     * 根据精确时间区间获取指定指数列表的历史分时数据（回放专用）
     * <p>
     * 与股票回放方法 {@link #getHistoryTrendDataByTimeRange} 对应，用于指数行情回放。
     * 复用 {@link HistoryTrendDTO} 作为返回类型，便于统一处理和推送到 Kafka。
     *
     * @param startTime 起始时间（格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（格式 yyyy-MM-dd HH:mm:ss）
     * @param indexList 指数代码列表（如 000300.SH, 000905.SH）
     * @return 历史分时数据
     */
    List<HistoryTrendDTO> getIndexHistoryTrendDataByTimeRange(String startTime, String endTime, List<String> indexList);

    /**
     * 根据时间区间获取指定指标列表的历史分时数据
     *
     * @param startDate 起始日期（格式 yyyyMMdd）
     * @param endDate   结束日期（格式 yyyyMMdd）
     * @param indexList 指标代码列表（为空时查询所有指标）
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

    /**
     * 获取指定交易日各指数的收盘价（即当日最后一条分时数据）
     * <p>
     * 用于昨收价缓存预热，返回交易日当天每个指数的收盘价。
     * 结果可作为下一个交易日的"昨收价"缓存到 Redis。
     *
     * @param tradeDate 交易日期（格式 yyyyMMdd）
     * @param indexList 指数代码列表（如 000300.SH, 000905.SH）
     * @return 指数收盘价列表（windCode + latestPrice）
     */
    List<HistoryTrendDTO> getIndexPreClosePrice(String tradeDate, List<String> indexList);

    /**
     * 获取指定时间区间内指定股票列表的当日最高价和最低价
     * <p>
     * 复用 {@link #getHistoryTrendDataByStockList} 获取分时数据，在内存中筛选每只股票的最高价和最低价。
     *
     * @param startDate 起始日期 (yyyyMMdd)
     * @param endDate   结束日期 (yyyyMMdd)
     * @param stockList 股票代码列表
     * @return Map，key 为股票代码，value 为包含最高价和最低价分时数据的 DTO
     */
    Map<String, DailyHighLowDTO> getDailyHighLowByStockList(String startDate, String endDate, List<String> stockList);

    /**
     * 获取指定股票列表在指定日期列表中每天的收盘价（当日最后一条分时数据）
     * <p>
     * 遍历日期列表，对每个日期查询指定股票的最后一条分时数据。
     *
     * @param stockList 股票代码列表
     * @param dateList  日期列表（格式 yyyyMMdd）
     * @return 嵌套 Map，外层 key 为日期，内层 key 为股票代码，value 为当日收盘价对应的完整分时数据
     */
    Map<String, Map<String, HistoryTrendDTO>> getDailyClosePriceByDateList(List<String> stockList, List<String> dateList);

    /**
     * 获取指定时间区间内每只股票每日的最高价、最低价、收盘价（时间序列格式）
     * <p>
     * 使用 TableRouter + ParallelQueryExecutor 实现跨表查询。
     * 返回格式适合策略计算，外层按股票分组，内层按日期排序。
     *
     * @param startDate 起始日期 (yyyyMMdd)
     * @param endDate   结束日期 (yyyyMMdd)
     * @param stockList 股票代码列表
     * @return Map，key 为股票代码，value 为按日期升序排列的 OHLC 数据列表
     */
    Map<String, List<DailyOhlcDTO>> getDailyOhlcByStockList(String startDate, String endDate, List<String> stockList);
}


