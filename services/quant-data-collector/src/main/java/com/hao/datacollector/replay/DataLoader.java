package com.hao.datacollector.replay;

import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import constants.DateTimeFormatConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final ReplayProperties config;

    private static final DateTimeFormatter COMPACT_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
    
    // 精确到秒的时间格式化器
    private static final DateTimeFormatter FULL_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.DEFAULT_DATETIME_FORMAT);

    /**
     * 加载指定时间段的行情数据
     *
     * @param start 起始时间（包含）
     * @param end   结束时间（包含）
     * @return 行情数据列表
     */
    public List<HistoryTrendDTO> loadTimeSlice(LocalDateTime start, LocalDateTime end) {
        // 修正边界重叠问题：
        // ReplayScheduler 的切分逻辑是 [start, start+5min]，下一次是 [start+5min, start+10min]
        // 数据库查询是闭区间 [start, end]
        // 因此，如果不处理，start+5min 这一秒的数据会被查询两次。
        // 解决方案：将查询的结束时间向前推 1 秒，变为 [start, end-1s]
        // 注意：如果 end 是当天的最终结束时间（例如 15:30:00），则不应减去，否则会丢失最后一秒的数据。
        // 这里简单判断：如果 end 的秒数是 00，且不是 15:30:00，则减去 1 秒。
        
        LocalDateTime queryEnd = end;
        // 简单的边界判断逻辑：如果不是收盘时间，且是整分，则减去1秒
        // 假设收盘时间是 15:30:00
        boolean isClosingTime = end.toLocalTime().equals(LocalTime.of(15, 30, 0));
        if (!isClosingTime && end.getSecond() == 0) {
            queryEnd = end.minusSeconds(1);
        }

        String startStr = start.format(FULL_FORMATTER);
        String endStr = queryEnd.format(FULL_FORMATTER);

        if (log.isDebugEnabled()) {
            log.debug("准备加载数据|Prepare_load_data,inputStart={},inputEnd={},queryStart={},queryEnd={}",
                    start, end, startStr, endStr);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 解析股票代码列表
        List<String> stockList = parseStockCodes();

        // 调用新增的精确时间查询接口
        List<HistoryTrendDTO> data = quotationService.getHistoryTrendDataByTimeRange(
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
