package com.hao.datacollector.cache;

import constants.DateTimeFormatConstants;
import util.DateUtil;
import com.hao.datacollector.service.BaseDataService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 日期相关数据缓存
 *
 * 设计目的：
 * 1. 缓存年度交易日历，降低重复查询开销。
 * 2. 提供统一的交易日历访问入口。
 *
 * 为什么需要该类：
 * - 交易日历是多处依赖的基础数据，需集中管理与复用。
 *
 * 核心实现思路：
 * - 启动时预加载不同年份的交易日历并缓存到静态列表。
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-06-14 17:34:21
 * @description: 日期相关数据缓存
 */
@Slf4j
@Component("DateCache")
public class DateCache {
    /**
     * 今年整年交易日历
     */
    public static List<LocalDate> ThisYearTradeDateList;

    /**
     * 年初至今的交易日历
     */
    public static List<LocalDate> CurrentYearTradeDateList;
    /**
     * 2020年的交易日历
     */
    public static List<LocalDate> Year2020TradeDateList;

    /**
     * 2021年的交易日历
     */
    public static List<LocalDate> Year2021TradeDateList;
    /**
     * 2022年的交易日历
     */
    public static List<LocalDate> Year2022TradeDateList;

    /**
     * 2023年的交易日历
     */
    public static List<LocalDate> Year2023TradeDateList;

    /**
     * 2024年的交易日历
     */
    public static List<LocalDate> Year2024TradeDateList;

    /**
     * 2025年的交易日历
     */
    public static List<LocalDate> Year2025TradeDateList;


    @Autowired
    private BaseDataService baseDataService;

    /**
     * 初始化交易日历缓存
     *
     * 实现逻辑：
     * 1. 计算年度起止日期。
     * 2. 按年份批量拉取交易日历并缓存。
     */
    @PostConstruct
    private void initDateList() {
        // 实现思路：
        // 1. 获取年度范围并加载交易日历。
        // 2. 将结果缓存到静态列表。
        //今年整年的交易日历
        String firstDayOfYear = DateUtil.getFirstDayOfYear(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        String lastDayOfYear = DateUtil.getLastDayOfYear(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        ThisYearTradeDateList = baseDataService.getTradeDateListByTime(firstDayOfYear, lastDayOfYear);
        //年初至今的交易日历
        String currentDay = DateUtil.getCurrentDateTime(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        CurrentYearTradeDateList = baseDataService.getTradeDateListByTime(firstDayOfYear, currentDay);
        //2020年的交易日历
        Year2020TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2020, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2020, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        //2021年的交易日历
        Year2021TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2021, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2021, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        //2022年的交易日历
        Year2022TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2022, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2022, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        //2023年的交易日历
        Year2023TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2023, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2023, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        //2024年的交易日历
        Year2024TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2024, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2024, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
        //2025年的交易日历
        Year2025TradeDateList = baseDataService.getTradeDateListByTime(DateUtil.getFirstDayOfYear(2025, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateUtil.getLastDayOfYear(2025, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));


        log.info("交易日历缓存完成|Trade_date_cache_loaded,thisYearSize={},currentYearSize={},year2022Size={},year2023Size={},year2024Size={}",
                ThisYearTradeDateList.size(), CurrentYearTradeDateList.size(), Year2022TradeDateList.size(),
                Year2023TradeDateList.size(), Year2024TradeDateList.size());
    }
}
