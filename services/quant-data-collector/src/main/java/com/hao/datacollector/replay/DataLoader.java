package com.hao.datacollector.replay;

import com.hao.datacollector.config.ReplayConfig;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import constants.DateTimeFormatConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 数据预加载器
 * <p>
 * 负责批量从 MySQL 加载行情数据到内存缓冲区。
 * 复用现有的 QuotationService 冷热表分离查询逻辑。
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader {

    private final QuotationService quotationService;
    private final ReplayConfig config;

    private static final DateTimeFormatter COMPACT_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    /**
     * 加载指定时间段的行情数据
     *
     * @param start 起始时间（包含）
     * @param end   结束时间（包含）
     * @return 行情数据列表
     */
    public List<HistoryTrendDTO> loadTimeSlice(LocalDateTime start, LocalDateTime end) {
        String startStr = start.format(COMPACT_FORMATTER);
        String endStr = end.format(COMPACT_FORMATTER);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 解析股票代码列表
        List<String> stockList = parseStockCodes();

        // 复用现有冷热表分离逻辑
        List<HistoryTrendDTO> data = quotationService.getHistoryTrendDataByStockList(
                startStr,
                endStr,
                stockList
        );

        // Null safety
        if (data == null) {
            data = Collections.emptyList();
        }

        stopWatch.stop();
        log.info("数据加载完成|Data_load_done,range={}-{},count={},elapsedMs={}",
                startStr, endStr, data.size(), stopWatch.getTotalTimeMillis());

        return data;
    }

    /**
     * 加载指定日期的全天行情数据（用于单日回放）
     *
     * @param date 日期字符串，格式 yyyyMMdd
     * @return 行情数据列表
     */
    public List<HistoryTrendDTO> loadFullDay(String date) {
        List<String> stockList = parseStockCodes();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<HistoryTrendDTO> data = quotationService.getHistoryTrendDataByStockList(
                date,
                date,
                stockList
        );

        // Null safety
        if (data == null) {
            data = Collections.emptyList();
        }

        stopWatch.stop();
        log.info("全天数据加载完成|Full_day_load_done,date={},count={},elapsedMs={}",
                date, data.size(), stopWatch.getTotalTimeMillis());

        return data;
    }

    /**
     * 解析配置中的股票代码列表
     *
     * @return 股票代码列表，为空表示全市场
     */
    private List<String> parseStockCodes() {
        if (!StringUtils.hasLength(config.getStockCodes())) {
            return Collections.emptyList();
        }
        return Arrays.asList(config.getStockCodes().split(","));
    }
}
