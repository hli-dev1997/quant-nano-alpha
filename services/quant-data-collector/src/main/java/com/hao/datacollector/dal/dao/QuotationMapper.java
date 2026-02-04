package com.hao.datacollector.dal.dao;

import com.hao.datacollector.dto.quotation.DailyOhlcDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;
import com.hao.datacollector.dto.table.quotation.QuotationStockBaseDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface QuotationMapper {
    /**
     * 批量插入基础行情数据
     *
     * @param quotationStockBaseList 行情数据列表
     * @return 插入数量
     */
    int insertQuotationStockBaseList(@Param("baseQuotationList") List<QuotationStockBaseDTO> quotationStockBaseList);

    /**
     * 获取指定时间内已转档的股票列表
     *
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return 已转档的股票列表
     */
    List<String> getJobQuotationBaseEndWindCodeList(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 批量插入历史分时行情数据
     *
     * @param historyTrendQuotationList 历史分时行情数据列表
     * @return 插入数量
     */
    int insertQuotationHistoryTrendList(@Param("historyTrendQuotationList") List<HistoryTrendDTO> historyTrendQuotationList);

    /**
     * 批量插入指标历史分时行情数据
     *
     * @param historyTrendQuotationList 历史分时行情数据列表
     * @return 插入数量
     */
    int insertQuotationIndexHistoryTrendList(@Param("historyTrendQuotationList") List<HistoryTrendIndexDTO> historyTrendQuotationList);

    /**
     * 获取指股票历史分时数据结束日期
     *
     * @return 当前年份最大日期yyyyMMdd
     */
    String getMaxHistoryTrendEndDate();

    /**
     * 获取指定年份的指标历史分时数据结束日期
     *
     * @param year 年份 yyyy
     * @return 当前年份最大日期yyyyMMdd
     */
    String getMaxHistoryIndexTrendEndDate(@Param("year") String year);

    /**
     * 获取指定日期已完成的股票列表
     *
     * @param maxEndDate 最大结束日期
     * @return 已完成的股票列表
     */
    List<String> getCompletedWindCodes(String maxEndDate);

    /**
     * 获取指定日期已完成的指标代码列表
     *
     * @param maxEndDate 最大结束日期
     * @return 已完成的股票列表
     */
    List<String> getCompletedIndexCodes(String maxEndDate);

    /**
     * 查询指定表内的历史分时数据
     *
     * @param tableName 表名（动态拼接）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param stockList 股票代码集合
     * @return 历史数据
     */
    List<HistoryTrendDTO> selectByWindCodeListAndDate(
            @Param("tableName") String tableName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("windCodeList") List<String> stockList
    );

    /**
     * 查询指定表内的每日收盘价（最后一条分时数据）
     *
     * @param tableName 表名（动态拼接）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param stockList 股票代码集合
     * @return 历史数据（仅包含 wind_code, trade_date, latest_price）
     */
    List<HistoryTrendDTO> selectDailyClosePriceByWindCodeListAndDate(
            @Param("tableName") String tableName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("windCodeList") List<String> stockList
    );

    /**
     * 查询指标历史分时数据
     *
     * @param startDate     开始日期
     * @param endDate       结束日期
     * @param indexCodeList 指标代码集合（为空时查询所有指标）
     * @return 指标历史分时数据
     */
    List<HistoryTrendIndexDTO> selectIndexByWindCodeListAndDate(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("indexCodeList") List<String> indexCodeList
    );

    /**
     * 查询指数历史分时数据（回放专用，返回 HistoryTrendDTO）
     * <p>
     * 与 {@link #selectByWindCodeListAndDate} 对应，用于指数行情回放。
     * 复用 HistoryTrendDTO 便于与股票行情统一处理。
     *
     * @param startTime     起始时间（精确到秒）
     * @param endTime       结束时间（精确到秒）
     * @param indexCodeList 指数代码集合
     * @return 指数历史分时数据
     */
    List<HistoryTrendDTO> selectIndexByTimeRange(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("indexCodeList") List<String> indexCodeList
    );

    /**
     * 查询指定交易日各指数的收盘价（即当日最后一条分时数据）
     * <p>
     * 用于昨收价缓存预热，返回交易日当天每个指数的最后一条数据的 latestPrice。
     *
     * @param tradeDate     交易日期（格式 yyyyMMdd）
     * @param indexCodeList 指数代码集合
     * @return 指数收盘价列表（windCode + latestPrice）
     */
    List<HistoryTrendDTO> selectIndexPreClosePrice(
            @Param("tradeDate") String tradeDate,
            @Param("indexCodeList") List<String> indexCodeList
    );

    /**
     * 查询指定时间区间内的每日最高价、最低价、收盘价
     * <p>
     * 使用窗口函数计算每只股票每日的 OHLC 数据。
     *
     * @param tableName 表名（动态拼接）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param stockList 股票代码集合
     * @return 每日 OHLC 数据列表
     */
    List<DailyOhlcDTO> selectDailyOhlcByStockListAndDate(
            @Param("tableName") String tableName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("windCodeList") List<String> stockList
    );
}

