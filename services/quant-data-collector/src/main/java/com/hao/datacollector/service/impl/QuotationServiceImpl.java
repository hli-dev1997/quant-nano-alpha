package com.hao.datacollector.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hao.datacollector.common.utils.HttpUtil;
import com.hao.datacollector.dal.dao.QuotationMapper;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.dto.quotation.HistoryTrendIndexDTO;
import com.hao.datacollector.dto.table.quotation.QuotationStockBaseDTO;
import com.hao.datacollector.properties.DataCollectorProperties;
import com.hao.datacollector.service.QuotationService;
import constants.DataSourceConstants;
import constants.DateTimeFormatConstants;
import enums.SpeedIndicatorEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import util.DateUtil;
import util.JsonUtil;
import util.MathUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 行情数据同步实现，涵盖基础行情与分时走势的抓取、解析与落库。
 * <p>
 * 统一策略：拼接 Wind 接口地址 → 通过 {@link HttpUtil} 发起请求 →
 * 转换原始 JSON/数组结构为内部 DTO → 使用 Mapper 批量写入数据库。
 * </p>
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-07-04 17:43:47
 * @description: 行情实现类
 */

/**
 * 实现思路：
 * <p>
 * 1. 通过配置化 URL 和认证信息调用 Wind 行情接口，获取基础与分时原始数据。
 * 2. 将原始 JSON/数组结构映射为业务 DTO，并进行必要的格式转换（时间、价格精度等）。
 * 3. 利用 Mapper 层提供的批量写入能力，将数据持久化，同时暴露查询与统计接口供外部调用。
 */

/**
 * 实现思路：
 * <p>
 * 1. 通过配置化 URL 和认证信息调用 Wind 行情接口，获取基础与分时原始数据。
 * 2. 将原始 JSON/数组结构映射为业务 DTO，并进行必要的格式转换（时间、价格精度等）。
 * 3. 利用 Mapper 层提供的批量写入能力，将数据持久化，同时暴露查询与统计接口供外部调用。
 */
@Slf4j
@Service
public class QuotationServiceImpl implements QuotationService {

    @Autowired
    private DataCollectorProperties properties;

    @Value("${wind_base.quotation.base.url}")
    private String QuotationBaseUrl;

    @Value("${wind_base.quotation.history.trend.url}")
    private String QuotationHistoryTrendUrl;

    @Autowired
    private QuotationMapper quotationMapper;

    @Resource(name = "ioTaskExecutor")
    private Executor ioTaskExecutor; // 注入IO密集型线程池

    /**
     * 请求成功标识
     */
    private static final String SUCCESS_FLAG = "200 OK";

    /**
     * 温热数据分界线：2024-01-01
     * 2024年及以后的数据在热表 (tb_quotation_history_hot)
     * 2024年以前的数据在温表 (tb_quotation_history_warm)
     */
    private static final LocalDate HOT_DATA_START_DATE = LocalDate.of(2024, 1, 1);
    private static final String TABLE_HOT = "tb_quotation_history_hot";
    private static final String TABLE_WARM = "tb_quotation_history_warm";

    /**
     * 获取基础行情数据
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 当前股票基础行情数据
     */
    @Override
    public Boolean transferQuotationBaseByStock(String windCode, String startDate, String endDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        String url = DataSourceConstants.WIND_PROD_WGQ + String.format(QuotationBaseUrl, windCode, startDate, endDate);
        ResponseEntity<String> response = HttpUtil.sendGetRequest(url, headers, 30000, 30000);
        // Wind 返回二维数组，每行是一日行情数据
        List<List<Long>> quotationList = JsonUtil.toType(response.getBody(), new TypeReference<List<List<Long>>>() {
        });
        List<QuotationStockBaseDTO> quotationStockBaseList = new ArrayList<>();
        if (quotationList == null || quotationList.isEmpty()) {
            log.warn("日志记录|Log_message,quotationData.quotationList_isEmpty()!windCode={}", windCode);
            return false;
        }
        for (List<Long> quotationData : quotationList) {
            if (quotationData.isEmpty()) {
                log.warn("日志记录|Log_message,quotationData.isEmpty()!windCode={}", windCode);
                continue;
            }
            // 将 Wind 返回的原始数值数组映射为结构化 DTO
            QuotationStockBaseDTO quotationStockBaseDTO = new QuotationStockBaseDTO();
            quotationStockBaseDTO.setWindCode(windCode);
            quotationStockBaseDTO.setTradeDate(DateUtil.parseToLocalDate(String.valueOf(quotationData.get(0)), DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT));
            //元
            quotationStockBaseDTO.setOpenPrice(MathUtil.shiftDecimal(quotationData.get(1).toString(), 2));
            //元
            quotationStockBaseDTO.setHighPrice(MathUtil.shiftDecimal(quotationData.get(2).toString(), 2));
            //元
            quotationStockBaseDTO.setLowPrice(MathUtil.shiftDecimal(quotationData.get(3).toString(), 2));
            //手
            quotationStockBaseDTO.setVolume(MathUtil.shiftDecimal(quotationData.get(4).toString(), 2));
            //元
            quotationStockBaseDTO.setAmount(MathUtil.shiftDecimal(quotationData.get(5).toString(), 0));
            //元
            quotationStockBaseDTO.setClosePrice(MathUtil.shiftDecimal(quotationData.get(6).toString(), 2));
            //%
            quotationStockBaseDTO.setTurnoverRate(MathUtil.shiftDecimal(quotationData.get(7).toString(), 2));
            quotationStockBaseList.add(quotationStockBaseDTO);
        }
        if (quotationStockBaseList.isEmpty()) {
            log.warn("日志记录|Log_message,transferQuotationBaseByStock_list=null!,windCode={}", windCode);
            return false;
        }
        // 批量写入日线基础信息
        int insertResult = quotationMapper.insertQuotationStockBaseList(quotationStockBaseList);
        return insertResult > 0;
    }

    /**
     * 转档股票历史分时数据
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 股票代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    @Override
    public Boolean transferQuotationHistoryTrend(int tradeDate, String windCodes, Integer dateType) {
        List<HistoryTrendDTO> quotationHistoryTrendList = getQuotationHistoryTrendList(tradeDate, windCodes, dateType);
        if (quotationHistoryTrendList.isEmpty()) {
            log.warn("日志记录|Log_message,quotationHistoryTrendList.isEmpty()!tradeDate={},windCodes={},dateType={}", tradeDate, windCodes, dateType);
            return false;
        }
        // Mapper 批量写入分时数据，避免重复网络请求
        int insertResult = quotationMapper.insertQuotationHistoryTrendList(quotationHistoryTrendList);
        return insertResult > 0;
    }

    /**
     * 转档指标历史分时数据
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 股票代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    @Override
    public Boolean transferQuotationIndexHistoryTrend(int tradeDate, String windCodes, Integer dateType) {
        List<HistoryTrendIndexDTO> quotationHistoryIndexTrendList = getQuotationHistoryIndexTrendList(tradeDate, windCodes, dateType);
        if (quotationHistoryIndexTrendList.isEmpty()) {
            log.warn("日志记录|Log_message,quotationHistoryIndexTrendList.isEmpty()!tradeDate={},windCodes={},dateType={}", tradeDate, windCodes, dateType);
            return false;
        }
        // 指标分时数据同样集中落库
        int insertResult = quotationMapper.insertQuotationIndexHistoryTrendList(quotationHistoryIndexTrendList);
        return insertResult > 0;
    }

    /**
     * 获取股票历史分时对象List
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 股票代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    private List<HistoryTrendDTO> getQuotationHistoryTrendList(int tradeDate, String windCodes, Integer dateType) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        String url = DataSourceConstants.WIND_PROD_WGQ + String.format(QuotationHistoryTrendUrl, tradeDate, windCodes, dateType);
        int retryCount = 0;
        int maxRetries = 2; // 最多重试2次
        ResponseEntity<String> response = null;
        while (retryCount <= maxRetries) {
            try {
                response = HttpUtil.sendGet(url, headers, 100000, 100000);
                break; // 成功则跳出循环
            } catch (Exception ex) {
                retryCount++;
                if (retryCount > maxRetries) {
                    // 超过最大重试次数，尝试从下一个ip获取
                    continue;
                }
                // 重试前等待一段时间
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // Wind 接口返回按股票->日期分组的嵌套结构，这里展开为 Map
        Map<String, Map<String, Object>> rawData = JsonUtil.toType(response.getBody(), new TypeReference<Map<String, Map<String, Object>>>() {
        });
        List<HistoryTrendDTO> allHistoryTrendList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> stockEntry : rawData.entrySet()) {
            String stockCode = stockEntry.getKey();
            Map<String, Object> stockData = stockEntry.getValue();
            for (Map.Entry<String, Object> dateEntry : stockData.entrySet()) {
                List<HistoryTrendDTO> historyTrendList = new ArrayList<>();
                String date = dateEntry.getKey();
                Object dateData = dateEntry.getValue();
                if (dateData == null || dateData.getClass().equals(Integer.class)) {
                    continue;
                }
                List<List<Integer>> dataArrays = (List<List<Integer>>) dateData;
                if (dataArrays.isEmpty()) continue;
                // 获取配置数组,只获取一次,indicatorIds.对应指标id元素下标数组,decimalShifts.对应关于每个位置元素精度小数位数(倒序)
                List<Integer> indicatorIds = new ArrayList<>();
                List<Integer> decimalShifts = new ArrayList<>();
                List<Integer> configArray = dataArrays.get(dataArrays.size() - 1);
                for (int i = 0; i < 5; i++) {
                    indicatorIds.add(configArray.get(i));
                    decimalShifts.add(configArray.get(i + 5));
                }
                Collections.reverse(decimalShifts);
                int time_s = 0;
                Double latestPrice = 0.00, averagePrice = 0.00;
                int sum = dataArrays.stream()
                        .filter(subList -> subList.size() > 1)
                        .mapToInt(subList -> subList.get(1))
                        .sum();
                if (sum > 160000) {
                    throw new RuntimeException("数据异常");
                }
                int timeIndex = indicatorIds.indexOf(2);
                int latestPriceIndex = indicatorIds.indexOf(3);
                int averagePriceIndex = indicatorIds.indexOf(79);
                int totalVolumeIndex = indicatorIds.indexOf(8);
                //剔除最后一行指标数据
                for (int i = 0; i < dataArrays.size() - 1; i++) {
                    HistoryTrendDTO historyTrendDTO = new HistoryTrendDTO();
                    time_s += dataArrays.get(i).get(timeIndex).intValue();
                    // 转换为LocalDateTime
                    LocalDate localDateTime = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    LocalTime time = LocalTime.of(
                            time_s / 10000,        // 小时: 13
                            (time_s % 10000) / 100, // 分钟: 38
                            time_s % 100           // 秒: 58
                    );
                    LocalDateTime dateTime = LocalDateTime.of(localDateTime, time);
                    historyTrendDTO.setTradeDate(dateTime);
                    historyTrendDTO.setWindCode(stockCode);
                    historyTrendDTO.setLatestPrice(latestPrice += dataArrays.get(i).get(latestPriceIndex));
                    historyTrendDTO.setAveragePrice(averagePrice += dataArrays.get(i).get(averagePriceIndex));
                    //总成交量不需要累加
                    historyTrendDTO.setTotalVolume(Double.valueOf(dataArrays.get(i).get(totalVolumeIndex)));
                    historyTrendList.add(historyTrendDTO);
                }
                //精度处理
                for (HistoryTrendDTO historyTrendDTO : historyTrendList) {
                    historyTrendDTO.setLatestPrice(MathUtil.formatDecimal(historyTrendDTO.getLatestPrice(), decimalShifts.get(latestPriceIndex), false));
                    historyTrendDTO.setAveragePrice(MathUtil.formatDecimal(historyTrendDTO.getAveragePrice(), decimalShifts.get(averagePriceIndex), false));
                    //成交额(买卖都算),由于A股市场都是以100股为单位/1手,故此在此固定/100
                    historyTrendDTO.setTotalVolume(MathUtil.formatDecimal(historyTrendDTO.getTotalVolume(), 2, false));
                }
                allHistoryTrendList.addAll(historyTrendList);
            }
        }
        log.info("日志记录|Log_message,getQuotationHistoryTrendList_allHistoryTrendList.size={}", allHistoryTrendList.size());
        return allHistoryTrendList;
    }

    /**
     * 获取指标历史分时对象List
     *
     * @param tradeDate 交易日期,如:20220608
     * @param windCodes 股票代码List
     * @param dateType  时间类型,0表示固定时间
     * @return 操作结果
     */
    private List<HistoryTrendIndexDTO> getQuotationHistoryIndexTrendList(int tradeDate, String windCodes, Integer dateType) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        String url = DataSourceConstants.WIND_PROD_WGQ + String.format(QuotationHistoryTrendUrl, tradeDate, windCodes, dateType);
        int retryCount = 0;
        int maxRetries = 2; // 最多重试2次
        ResponseEntity<String> response = null;
        while (retryCount <= maxRetries) {
            try {
                response = HttpUtil.sendGet(url, headers, 100000, 100000);
                break; // 成功则跳出循环
            } catch (Exception ex) {
                retryCount++;
                if (retryCount > maxRetries) {
                    break; // 超过最大重试次数，退出循环
                }
                // 重试前等待一段时间
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("请求被中断", ie);
                }
            }
        }
        if (response == null || response.getBody() == null) {
            throw new RuntimeException("请求失败，无法获取数据");
        }
        // 处理响应数据结构 - 先解析外层包装
        String responseBody = response.getBody();
        String actualDataJson = responseBody;
        try {
            // 检查是否有外层包装
            Map<String, Object> wrapper = JsonUtil.toMap(responseBody, String.class, Object.class);
            if (wrapper != null && wrapper.containsKey("body")) {
                Object bodyObj = wrapper.get("body");
                if (bodyObj instanceof String) {
                    actualDataJson = (String) bodyObj;
                } else {
                    actualDataJson = JsonUtil.toJson(bodyObj);
                }
            }
        } catch (Exception e) {
            // 如果解析失败，使用原始响应体
            log.warn("解析外层包装失败，使用原始响应体:_{}|Log_message", e.getMessage());
        }
        // 解析实际数据
        Map<String, Map<String, Object>> rawData;
        try {
            rawData = JsonUtil.toType(actualDataJson, new TypeReference<Map<String, Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.error("解析JSON数据失败:_{}", e.getMessage(), e);
            throw new RuntimeException("数据解析失败", e);
        }
        if (rawData == null || rawData.isEmpty()) {
            log.warn("解析后的数据为空|Log_message");
            return new ArrayList<>();
        }
        List<HistoryTrendIndexDTO> allHistoryIndexTrendList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> stockEntry : rawData.entrySet()) {
            String stockCode = stockEntry.getKey();
            Map<String, Object> stockData = stockEntry.getValue();
            if (stockData == null) {
                continue;
            }
            for (Map.Entry<String, Object> dateEntry : stockData.entrySet()) {
                String date = dateEntry.getKey();
                Object dateData = dateEntry.getValue();
                // 跳过空数据或非数组数据
                if (dateData == null || dateData instanceof Integer) {
                    continue;
                }
                // 安全转换为List<List<Number>>
                List<List<Number>> dataArrays;
                try {
                    dataArrays = JsonUtil.toType(JsonUtil.toJson(dateData), new TypeReference<List<List<Number>>>() {
                    });
                } catch (Exception e) {
                    log.warn("转换数据数组失败,_stockCode:_{},_date:_{},_error:_{}", stockCode, date, e.getMessage());
                    continue;
                }
                if (dataArrays == null || dataArrays.isEmpty()) {
                    continue;
                }

                try {
                    List<HistoryTrendIndexDTO> historyIndexTrendList = processStockDateData(stockCode, date, dataArrays);
                    allHistoryIndexTrendList.addAll(historyIndexTrendList);
                } catch (Exception e) {
                    log.error("处理股票数据失败,_stockCode:_{},_date:_{},_error:_{}", stockCode, date, e.getMessage(), e);
                    // 继续处理其他数据，不中断整个流程
                }
            }
        }
        log.info("日志记录|Log_message,getQuotationHistoryTrendList_allHistoryTrendList.size={}", allHistoryIndexTrendList.size());
        return allHistoryIndexTrendList;
    }

    private List<HistoryTrendIndexDTO> processStockDateData(String stockCode, String date, List<List<Number>> dataArrays) {
        List<HistoryTrendIndexDTO> historyIndexTrendList = new ArrayList<>();
        // 获取配置数组 - 最后一行
        List<Number> configArray = dataArrays.get(dataArrays.size() - 1);
        if (configArray == null || configArray.size() < 10) {
            throw new RuntimeException("配置数组格式不正确");
        }
        // 解析配置
        List<Integer> indicatorIds = new ArrayList<>();
        List<Integer> decimalShifts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            indicatorIds.add(configArray.get(i).intValue());
            decimalShifts.add(configArray.get(i + 5).intValue());
        }
        Collections.reverse(decimalShifts);
        // 数据异常检测
        long sum = dataArrays.stream()
                .filter(subList -> subList != null && subList.size() > 1 && subList.get(1) != null)
                .mapToLong(subList -> subList.get(1).longValue())
                .sum();

        if (sum > 160000) {
            throw new RuntimeException("数据异常，sum: " + sum);
        }
        // 获取各指标的索引位置
        int timeIndex = indicatorIds.indexOf(SpeedIndicatorEnum.TRADE_TIME.getIndicator());
        //最新价
        int latestPriceIndex = indicatorIds.indexOf(SpeedIndicatorEnum.NEW_PRICE.getIndicator());
        //总成交额
        int totalAmount = indicatorIds.indexOf(SpeedIndicatorEnum.TOTAL_AMOUNT.getIndicator());
        //总成交量
        int totalVolume = indicatorIds.indexOf(SpeedIndicatorEnum.TOTAL_VOLUME.getIndicator());
        // 检查必要的索引是否存在
        if (timeIndex < 0 || latestPriceIndex < 0) {
            throw new RuntimeException("缺少必要的指标索引");
        }
        // 解析日期
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception e) {
            throw new RuntimeException("日期解析失败: " + date, e);
        }
        // 处理每一行数据（排除最后一行配置数据）
        int time_s = 0;
        double latestPrice = 0.0;
        for (int i = 0; i < dataArrays.size() - 1; i++) {
            List<Number> row = dataArrays.get(i);
            if (row == null || row.isEmpty()) {
                continue;
            }
            // 检查行数据完整性
            int maxIndex = Math.max(Math.max(timeIndex, latestPriceIndex),
                    Math.max(totalAmount >= 0 ? totalAmount : 0,
                            totalVolume >= 0 ? totalVolume : 0));
            if (row.size() <= maxIndex) {
                log.warn("行数据不完整，跳过该行:_stockCode={},_date={},_rowIndex={}", stockCode, date, i);
                continue;
            }
            try {
                // 累加时间
                Number timeNum = row.get(timeIndex);
                if (timeNum != null) {
                    time_s += timeNum.intValue();
                }
                // 解析时间
                int hours = time_s / 10000;
                int minutes = (time_s % 10000) / 100;
                int seconds = time_s % 100;
                // 验证时间有效性
                if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
                    log.warn("时间数据异常，跳过该行:_time_s={}", time_s);
                    continue;
                }
                LocalTime time = LocalTime.of(hours, minutes, seconds);
                LocalDateTime dateTime = LocalDateTime.of(localDate, time);

                // 创建DTO
                HistoryTrendIndexDTO historyTrendIndexDTO = new HistoryTrendIndexDTO();
                historyTrendIndexDTO.setTradeDate(dateTime);
                historyTrendIndexDTO.setWindCode(stockCode);

                // 设置价格数据（累加）
                Number latestPriceNum = row.get(latestPriceIndex);
                if (latestPriceNum != null) {
                    latestPrice += latestPriceNum.doubleValue();
                }
                historyTrendIndexDTO.setLatestPrice(latestPrice);

                // 设置成交额数据（不累加）
                if (totalAmount >= 0) {
                    Number averagePriceNum = row.get(totalAmount);
                    if (averagePriceNum != null) {
                        historyTrendIndexDTO.setTotalAmount(averagePriceNum.doubleValue());
                    } else {
                        historyTrendIndexDTO.setTotalAmount(0.0);
                    }
                } else {
                    historyTrendIndexDTO.setTotalAmount(0.0);
                }

                // 设置成交量数据（不累加）
                if (totalVolume >= 0) {
                    Number volumeNum = row.get(totalVolume);
                    if (volumeNum != null) {
                        historyTrendIndexDTO.setTotalVolume(volumeNum.doubleValue());
                    } else {
                        historyTrendIndexDTO.setTotalVolume(0.0);
                    }
                } else {
                    historyTrendIndexDTO.setTotalVolume(0.0);
                }
                historyIndexTrendList.add(historyTrendIndexDTO);

            } catch (Exception e) {
                log.error("处理行数据失败:_stockCode={},_date={},_rowIndex={},_error={}",
                        stockCode, date, i, e.getMessage(), e);
                // 继续处理下一行
            }
        }

        // 精度处理
        for (HistoryTrendIndexDTO historyTrendDTO : historyIndexTrendList) {
            try {
                // 处理最新价精度
                if (latestPriceIndex >= 0 && latestPriceIndex < decimalShifts.size()) {
                    historyTrendDTO.setLatestPrice(MathUtil.formatDecimal(
                            historyTrendDTO.getLatestPrice(), decimalShifts.get(latestPriceIndex), false));
                } else {
                    historyTrendDTO.setLatestPrice(MathUtil.formatDecimal(
                            historyTrendDTO.getLatestPrice(), 2, false));
                }
                // 处理成交额精度
                if (totalAmount >= 0 && totalAmount < decimalShifts.size()) {
                    historyTrendDTO.setTotalAmount(MathUtil.formatDecimal(
                            historyTrendDTO.getTotalAmount(), decimalShifts.get(totalAmount), false));
                } else {
                    historyTrendDTO.setTotalAmount(MathUtil.formatDecimal(
                            historyTrendDTO.getTotalAmount(), 2, false));
                }
                // 处理成交量精度
                historyTrendDTO.setTotalVolume(MathUtil.formatDecimal(
                        historyTrendDTO.getTotalVolume(), 2, false));

            } catch (Exception e) {
                log.error("精度处理失败:_stockCode={},_date={},_error={}", stockCode, date, e.getMessage(), e);
            }
        }
        return historyIndexTrendList;
    }


    /**
     * 根据时间区间获取A股历史分时数据
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 历史分时数据
     */
    @Override
    public List<HistoryTrendDTO> getHistoryTrendDataByDate(String startDate, String endDate) {

        if (!StringUtils.hasLength(endDate)) {
            endDate = DateUtil.getCurrentDateTimeByStr(DateTimeFormatConstants.COMPACT_DATE_FORMAT);
        }
        return quotationMapper.getHistoryTrendDataByDate(startDate, endDate);
    }

    /**
     * 根据时间区间获取指定股票列表的A股历史分时数据
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param stockList 股票列表
     * @return 历史分时数据
     */
    @Override
    public List<HistoryTrendDTO> getHistoryTrendDataByStockList(String startDate, String endDate, List<String> stockList) {
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        LocalDate start = LocalDate.parse(startDate, pattern);
        LocalDate end = LocalDate.parse(endDate, pattern);
        // 判断查询范围
        boolean queryWarm = start.isBefore(HOT_DATA_START_DATE);
        boolean queryHot = end.isAfter(HOT_DATA_START_DATE) || end.isEqual(HOT_DATA_START_DATE);

        // 对 endDate 追加当天最后一秒，确保查询覆盖当天所有数据
        String endDateWithTime = DateUtil.appendEndOfDayTime(endDate);

        // 1. 仅查询热表
        if (queryHot && !queryWarm) {
            log.info("查询热数据表|Query_hot_table,range={}-{},stocks={}", startDate, endDateWithTime, stockList.size());
            return quotationMapper.selectByWindCodeListAndDate(TABLE_HOT, startDate, endDateWithTime, stockList);
        }
        // 2. 仅查询温表
        if (queryWarm && !queryHot) {
            log.info("查询温数据表|Query_warm_table,range={}-{},stocks={}", startDate, endDateWithTime, stockList.size());
            return quotationMapper.selectByWindCodeListAndDate(TABLE_WARM, startDate, endDateWithTime, stockList);
        }
        // 3. 跨冷热表查询，使用CompletableFuture并行执行
        if (queryWarm && queryHot) {
            log.info("并行查询冷热数据表|Parallel_query_hot_warm_tables,range={}-{},stocks={}", startDate, endDateWithTime, stockList.size());
            // 定义温表查询范围（温表的 endDate 也需要追加时间）
            LocalDate warmEnd = HOT_DATA_START_DATE.minusDays(1);
            String warmEndDateStr = DateUtil.appendEndOfDayTime(warmEnd.format(pattern));
            // 定义热表查询范围
            String hotStartDateStr = HOT_DATA_START_DATE.format(pattern);
            // 异步查询温表
            CompletableFuture<List<HistoryTrendDTO>> warmFuture = CompletableFuture.supplyAsync(() -> {
                log.info("异步查询温表开始|Async_query_warm_start,range={}-{}", startDate, warmEndDateStr);
                return quotationMapper.selectByWindCodeListAndDate(TABLE_WARM, startDate, warmEndDateStr, stockList);
            }, ioTaskExecutor);
            // 异步查询热表（使用追加时间后的 endDate）
            CompletableFuture<List<HistoryTrendDTO>> hotFuture = CompletableFuture.supplyAsync(() -> {
                log.info("异步查询热表开始|Async_query_hot_start,range={}-{}", hotStartDateStr, endDateWithTime);
                return quotationMapper.selectByWindCodeListAndDate(TABLE_HOT, hotStartDateStr, endDateWithTime, stockList);
            }, ioTaskExecutor);
            // 等待两个查询都完成
            CompletableFuture.allOf(warmFuture, hotFuture).join();
            try {
                List<HistoryTrendDTO> warmData = warmFuture.get();
                List<HistoryTrendDTO> hotData = hotFuture.get();

                // 创建一个足够大的列表并按顺序合并结果，保证时间连续性
                List<HistoryTrendDTO> combinedResult = new ArrayList<>(warmData.size() + hotData.size());
                combinedResult.addAll(warmData); // 温数据（旧）在前
                combinedResult.addAll(hotData);  // 热数据（新）在后
                log.info("冷热数据合并完成|Merge_hot_warm_data_done,warmCount={},hotCount={},totalCount={}", warmData.size(), hotData.size(), combinedResult.size());
                return combinedResult;
            } catch (Exception e) {
                log.error("并行查询冷热数据失败|Parallel_query_failed", e);
                Thread.currentThread().interrupt(); // 恢复中断状态
                return Collections.emptyList();
            }
        }
        // 默认返回空列表
        return Collections.emptyList();
    }
}
