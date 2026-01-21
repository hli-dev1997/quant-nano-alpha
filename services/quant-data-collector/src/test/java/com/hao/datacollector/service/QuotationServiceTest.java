package com.hao.datacollector.service;

import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.cache.StockCache;
import com.hao.datacollector.dal.dao.QuotationMapper;
import constants.DateTimeFormatConstants;
import enums.market.RiskMarketIndexEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import util.DateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest
class QuotationServiceTest {

    @Autowired
    private QuotationService quotationService;
    @Autowired
    private QuotationMapper quotationMapper;

    @Test
    void transferQuotationBaseByStock() {
        List<String> allWindCodeList = new ArrayList<>(StockCache.allWindCode);
//        String startDate = DateUtil.formatLocalDate(DateCache.CurrentYearTradeDateList.get(0), DateTimeFormatConstant.EIGHT_DIGIT_DATE_FORMAT);
        String startDate = "20250718";
        String endDate = DateUtil.stringTimeToAdjust(DateUtil.getCurrentDateTimeByStr(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT), DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT, 1);
        //需要剔除已经转档的股票
        List<String> endWindCodeList = quotationMapper.getJobQuotationBaseEndWindCodeList(startDate, endDate);
        allWindCodeList.removeAll(endWindCodeList);
        for (String windCode : allWindCodeList) {
            Boolean transferResult = quotationService.transferQuotationBaseByStock(windCode, startDate, endDate);
            log.info("日志记录|Log_message,transferQuotationBaseByStock_result={},windCode={},startDate={},endDate={}", transferResult, windCode, startDate, endDate);
        }
    }

    //todo 修改全部选取,转档hot表，待实际测验
    @Test
    void transferQuotationHistoryTrend() {
        List<String> allWindCodeList = new ArrayList<>(StockCache.allWindCode);
        List<String> yearTradeDateList = DateUtil.formatLocalDateList(DateCache.CurrentYearTradeDateList, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        //从当年已转档的最大日期(包含),并且剔除最大日期已经转档过的windCode,继续开始转档
        String maxEndDate = quotationMapper.getMaxHistoryTrendEndDate();
//        String maxEndDate = "20260105";
        List<String> completedWindCodes = quotationMapper.getCompletedWindCodes(maxEndDate);
        int tradeDateIndexOf = yearTradeDateList.indexOf(maxEndDate);
        int batchSize = 100;
        if (tradeDateIndexOf != -1) {
            List<String> needFillBack = allWindCodeList.stream()
                    .filter(code -> !completedWindCodes.contains(code))
                    .collect(Collectors.toList());
            if (!needFillBack.isEmpty()) {
                log.info("补偿转档_{}_日期未完成的_{}_个_windCode", maxEndDate, needFillBack.size());
                List<String> needFillBackTradeDateList = new ArrayList<>();
                needFillBackTradeDateList.add(maxEndDate);
                transferOneDay(needFillBackTradeDateList, needFillBack, batchSize);
            }
            // 正常转档后续日期
            yearTradeDateList = new ArrayList<>(yearTradeDateList.subList(tradeDateIndexOf + 1, yearTradeDateList.size()));
            transferOneDay(yearTradeDateList, allWindCodeList, batchSize);
        }
    }

    //todo tradeDate=20240528,_windCodes=920961.BJ
    @Test
    void transferQuotationHistoryTrendSupplement() {
        // 使用补充的WindCode列表
        List<String> supplementWindCodeList = new ArrayList<>(StockCache.supplementWindCode);
        if (supplementWindCodeList.isEmpty()) {
            log.info("补充转档列表为空，无需执行");
            return;
        }
        // 获取2024年至今的交易日历
        List<String> currentYearTradeDateList = DateUtil.formatLocalDateList(DateCache.CurrentYearTradeDateList, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        List<String> allTradeDateList = new ArrayList<>();
        allTradeDateList.addAll(currentYearTradeDateList);
        // 去重并排序
        allTradeDateList = allTradeDateList.stream().distinct().sorted().collect(Collectors.toList());
        // startDate从2024年开始Year2024TradeDateList的第一个元素
        String startDate = "20260105";
        // 过滤出startDate之后的日期
        List<String> targetTradeDateList = allTradeDateList.stream()
                .filter(date -> date.compareTo(startDate) >= 0)
                .collect(Collectors.toList());
        int batchSize = 100;
        log.info("开始补充转档，共{}个股票，{}个交易日", supplementWindCodeList.size(), targetTradeDateList.size());
        transferOneDay(targetTradeDateList, supplementWindCodeList, batchSize);
    }

    private void transferOneDay(List<String> yearTradeDateList, List<String> windCodes, int batchSize) {
        int totalSize = windCodes.size();
        for (String tradeDate : yearTradeDateList) {
            for (int i = 0; i < totalSize; i += batchSize) {
                List<String> subList = windCodes.subList(i, Math.min(i + batchSize, totalSize));
                String windCodeStr = String.join(",", subList);
                Boolean transferResult = quotationService.transferQuotationHistoryTrend(Integer.parseInt(tradeDate), windCodeStr, 0);
                log.info("日志记录|Log_message,transferQuotationHistoryTrend_result={},_tradeDate={},_windCodes={}", transferResult, tradeDate, windCodeStr);
            }
        }
    }

    @Test
    void transferQuotationHistoryTrendMarketIndex() {
        List<String> allIndexCodeList = RiskMarketIndexEnum.ALL_CODES;
        List<String> yearTradeDateList = DateUtil.formatLocalDateList(DateCache.CurrentYearTradeDateList, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        //从当年已转档的最大日期(包含),并且剔除最大日期已经转档过的windCode,继续开始转档
        String maxEndDate = quotationMapper.getMaxHistoryIndexTrendEndDate("2026");
//        String maxEndDate = "20260105";
        List<String> completedWindCodes = quotationMapper.getCompletedIndexCodes(maxEndDate);
        int tradeDateIndexOf = yearTradeDateList.indexOf(maxEndDate);
        int batchSize = 100;
        if (tradeDateIndexOf != -1) {
            List<String> needFillBack = allIndexCodeList.stream()
                    .filter(code -> !completedWindCodes.contains(code))
                    .collect(Collectors.toList());
            if (!needFillBack.isEmpty()) {
                log.info("补偿转档_{}_日期未完成的_{}_个_windCode", maxEndDate, needFillBack.size());
                List<String> needFillBackTradeDateList = new ArrayList<>();
                needFillBackTradeDateList.add(maxEndDate);
                transferOneDayMarketIndex(needFillBackTradeDateList, needFillBack, batchSize);
            }
            // 正常转档后续日期
            yearTradeDateList = new ArrayList<>(yearTradeDateList.subList(tradeDateIndexOf + 1, yearTradeDateList.size()));
            transferOneDayMarketIndex(yearTradeDateList, allIndexCodeList, batchSize);
        }
    }

    private void transferOneDayMarketIndex(List<String> yearTradeDateList, List<String> windCodes, int batchSize) {
        int totalSize = windCodes.size();
        for (String tradeDate : yearTradeDateList) {
            if (tradeDate.contains("2027")) {
                log.error("日志记录|Log_message,out!,tradeDate={}", tradeDate);
                throw new RuntimeException("2027!!!!");
            }
            for (int i = 0; i < totalSize; i += batchSize) {
                List<String> subList = windCodes.subList(i, Math.min(i + batchSize, totalSize));
                String windCodeStr = String.join(",", subList);
                Boolean transferResult = quotationService.transferQuotationIndexHistoryTrend(Integer.parseInt(tradeDate), windCodeStr, 0);
                log.info("日志记录|Log_message,transferQuotationHistoryTrend_result={},_tradeDate={},_windCodes={}", transferResult, tradeDate, windCodeStr);
            }
        }
    }


    @Test
    public void getHistoryTrendDataByStockList() {
//        List<String> allWindCodeList = new ArrayList<>(StockCache.allWindCode);
//        List<HistoryTrendDTO> trendDTOS = quotationMapper.selectByWindCodeListAndDate("tb_quotation_history_trend_202508", "20250801", "20250802", allWindCodeList);
//        log.info("日志记录|Log_message,trendDTOS={}", trendDTOS.size());
    }
}
