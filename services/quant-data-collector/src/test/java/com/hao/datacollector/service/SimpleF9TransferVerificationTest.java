//package com.hao.datacollector.service;
//
//import com.hao.datacollector.dal.dao.SimpleF9Mapper;
//import com.hao.datacollector.dto.param.f9.F9Param;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.time.LocalDate;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
///**
// * F9 数据转档专用验证测试类 (仅验证2个股票)
// * <p>
// * 验证流程：
// * 1. 初始化目标股票：600519.SH (贵州茅台), 000001.SZ (平安银行)
// * 2. 模拟去重：查询当天已转档数据 -> 剔除 -> 剩余未转档数据
// * 3. 执行转档：对剩余数据调用转档接口
// * 4. 再次去重：验证是否全部剔除成功
// */
//@Slf4j
//@SpringBootTest
//public class SimpleF9TransferVerificationTest {
//
//    @Autowired
//    private SimpleF9Service simpleF9Service;
//
//    @Autowired
//    private SimpleF9Mapper simpleF9Mapper;
//
//    // 目标测试股票代码
//    private static final List<String> TARGET_WIND_CODES = Arrays.asList("600519.SH", "601318.SH");
//
//    /**
//     * 通用转档验证方法
//     *
//     * @param jobName 转档任务名称
//     * @param existingCodesGetter 获取当天已转档代码的函数接口
//     * @param transferAction 执行转档动作的函数接口
//     */
//    private void verifyTransferJob(String jobName, ExistingCodesGetter existingCodesGetter, TransferAction transferAction) {
//        String today = LocalDate.now().toString();
//        log.info("========== 开始验证任务: {} ==========", jobName);
//
//        // 1. 获取当天已转档列表
//        List<String> existingCodes = existingCodesGetter.get(today);
//        log.info("[{}] 当前已转档数量: {}", jobName, existingCodes.size());
//        if (existingCodes.containsAll(TARGET_WIND_CODES)) {
//            log.info("[{}] 目标股票已全部存在，无需转档 (验证去重逻辑成功)", jobName);
//            return;
//        }
//
//        // 2. 剔除已存在，计算待处理列表
//        List<String> todoList = new ArrayList<>(TARGET_WIND_CODES);
//        todoList.removeAll(existingCodes);
//        log.info("[{}] 待处理股票: {}", jobName, todoList);
//
//        if (todoList.isEmpty()) {
//            log.info("[{}] 所有目标股票今日已处理完毕", jobName);
//        } else {
//            // 3. 执行转档
//            for (String code : todoList) {
//                F9Param param = new F9Param();
//                param.setWindCode(code);
//                try {
//                    Boolean result = transferAction.execute(param);
//                    log.info("[{}] 转档结果: code={}, result={}", jobName, code, result);
//                } catch (Exception e) {
//                    log.error("[{}] 转档异常: code={}, msg={}", jobName, code, e.getMessage());
//                }
//            }
//        }
//
//        // 4. 二次查询验证
//        List<String> finalExistingCodes = existingCodesGetter.get(today);
//        List<String> remaining = new ArrayList<>(TARGET_WIND_CODES);
//        remaining.removeAll(finalExistingCodes);
//        if (remaining.isEmpty()) {
//            log.info("[{}] 验证通过，目标股票全部入库", jobName);
//        } else {
//            log.warn("[{}] 验证不完整，仍有未入库股票: {}", jobName, remaining);
//        }
//        log.info("========== 结束验证任务: {} ==========\n", jobName);
//    }
//
//    @FunctionalInterface
//    interface ExistingCodesGetter {
//        List<String> get(String date);
//    }
//
//    @FunctionalInterface
//    interface TransferAction {
//        Boolean execute(F9Param param);
//    }
//
//    // ==================== 验证方法 ====================
//
////    @Test
////    void verifyCompanyProfile() {
////        verifyTransferJob("公司概览",
////            simpleF9Mapper::getInsertFinancialSummaryData,
////            simpleF9Service::insertCompanyProfileDataJob);
////    }
//
//    @Test
//    void verifyInformation() {
//        verifyTransferJob("资讯信息",
//            simpleF9Mapper::getInsertedInformationWindCodes,
//            simpleF9Service::insertInformationDataJob);
//    }
//
//    @Test
//    void verifyKeyStatistics() {
//        verifyTransferJob("关键统计",
//            simpleF9Mapper::getInsertedKeyStatisticsWindCodes,
//            simpleF9Service::insertKeyStatisticsDataJob);
//    }
//
//    @Test
//    void verifyCompanyInfo() {
//        verifyTransferJob("公司信息",
//            simpleF9Mapper::getInsertedCompanyInfoWindCodes,
//            simpleF9Service::insertCompanyInfoDataJob);
//    }
//
//    @Test
//    void verifyNotice() {
//        verifyTransferJob("公告数据",
//            simpleF9Mapper::getInsertedNoticeWindCodes,
//            simpleF9Service::insertNoticeDataJob);
//    }
//
//    @Test
//    void verifyGreatEvent() {
//        verifyTransferJob("大事数据",
//            simpleF9Mapper::getInsertedGreatEventWindCodes,
//            simpleF9Service::insertGreatEventDataJob);
//    }
//
//    @Test
//    void verifyProfitForecast() {
//        verifyTransferJob("盈利预测",
//            simpleF9Mapper::getInsertedProfitForecastWindCodes,
//            simpleF9Service::insertProfitForecastDataJob);
//    }
//
//    @Test
//    void verifyMarketPerformance() {
//        verifyTransferJob("市场表现",
//            simpleF9Mapper::getInsertedMarketPerformanceWindCodes,
//            simpleF9Service::insertMarketPerformanceDataJob);
//    }
//
//    @Test
//    void verifyPeBand() {
//        verifyTransferJob("PE估值带",
//            simpleF9Mapper::getInsertedPeBandWindCodes,
//            simpleF9Service::insertPeBandDataJob);
//    }
//
//    @Test
//    void verifyValuationIndex() {
//        verifyTransferJob("估值指标",
//            simpleF9Mapper::getInsertedValuationIndexWindCodes,
//            simpleF9Service::insertSecurityMarginDataJob);
//    }
//
//    @Test
//    void verifyFinancialSummary() {
//        verifyTransferJob("成长能力",
//            simpleF9Mapper::getInsertedFinancialSummaryWindCodes,
//            simpleF9Service::insertFinancialSummaryDataJob);
//    }
//}
