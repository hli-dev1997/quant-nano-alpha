package com.hao.datacollector.dal.dao;

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
}
