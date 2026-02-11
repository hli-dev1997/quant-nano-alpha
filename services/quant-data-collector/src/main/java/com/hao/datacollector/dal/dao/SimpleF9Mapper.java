package com.hao.datacollector.dal.dao;

import com.hao.datacollector.dto.table.f9.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F9数据转档Mapper
 *
 * @author LiHao
 */
public interface SimpleF9Mapper {

    // ==================== 公司概览 ====================

    /**
     * 批量插入公司简介转档数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertCompanyProfileDataJob(@Param("param") List<InsertCompanyProfileDTO> param);

    /**
     * 获取已转档公司简介的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertFinancialSummaryData(@Param("today") String today);

    // ==================== 资讯信息 ====================

    /**
     * 批量插入资讯信息
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertInformation(@Param("param") List<InsertInformationDTO> param);

    /**
     * 获取已转档资讯信息的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedInformationWindCodes(@Param("today") String today);

    // ==================== 关键统计 ====================

    /**
     * 批量插入关键统计数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertKeyStatistics(@Param("param") List<InsertKeyStatisticsDTO> param);

    /**
     * 获取已转档关键统计的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedKeyStatisticsWindCodes(@Param("today") String today);

    // ==================== 公司信息 ====================

    /**
     * 批量插入公司信息
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertCompanyInfo(@Param("param") List<InsertCompanyInfoDTO> param);

    /**
     * 获取已转档公司信息的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedCompanyInfoWindCodes(@Param("today") String today);

    // ==================== 公告 ====================

    /**
     * 批量插入公告数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertNotice(@Param("param") List<InsertNoticeDTO> param);

    /**
     * 获取已转档公告的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedNoticeWindCodes(@Param("today") String today);

    // ==================== 大事 ====================

    /**
     * 批量插入大事数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertGreatEvent(@Param("param") List<InsertGreatEventDTO> param);

    /**
     * 获取已转档大事的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedGreatEventWindCodes(@Param("today") String today);

    // ==================== 盈利预测 ====================

    /**
     * 批量插入盈利预测数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertProfitForecast(@Param("param") List<InsertProfitForecastDTO> param);

    /**
     * 获取已转档盈利预测的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedProfitForecastWindCodes(@Param("today") String today);

    // ==================== 市场表现 ====================

    /**
     * 批量插入市场表现数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertMarketPerformance(@Param("param") List<InsertMarketPerformanceDTO> param);

    /**
     * 获取已转档市场表现的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedMarketPerformanceWindCodes(@Param("today") String today);

    // ==================== PE_BAND ====================

    /**
     * 批量插入PE_BAND数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertPeBand(@Param("param") List<InsertPeBandDTO> param);

    /**
     * 获取已转档PE_BAND的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedPeBandWindCodes(@Param("today") String today);

    // ==================== 估值指标 ====================

    /**
     * 批量插入估值指标数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertValuationIndex(@Param("param") List<InsertValuationIndexDTO> param);

    /**
     * 获取已转档估值指标的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedValuationIndexWindCodes(@Param("today") String today);

    // ==================== 成长能力 ====================

    /**
     * 批量插入成长能力数据
     *
     * @param param 转档对象列表
     * @return 影响行数
     */
    int batchInsertFinancialSummary(@Param("param") List<InsertFinancialSummaryDTO> param);

    /**
     * 获取已转档成长能力的windCode
     *
     * @return 已转档windCode列表
     */
    List<String> getInsertedFinancialSummaryWindCodes(@Param("today") String today);
}
