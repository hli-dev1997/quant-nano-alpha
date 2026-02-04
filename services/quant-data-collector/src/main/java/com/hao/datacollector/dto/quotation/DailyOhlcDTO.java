package com.hao.datacollector.dto.quotation;

import com.fasterxml.jackson.annotation.JsonFormat;
import constants.DateTimeFormatConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 每日 OHLC（Open/High/Low/Close）数据传输对象
 * <p>
 * 用于返回指定时间区间内每只股票每日的最高价、最低价、收盘价。
 *
 * @author hli
 * @date 2026-02-04
 */
@Data
@Schema(description = "每日最高价、最低价、收盘价数据传输对象")
public class DailyOhlcDTO {

    @Schema(description = "股票代码", example = "600519.SH")
    private String windCode;

    @Schema(description = "交易日期", example = "2024-06-03")
    @JsonFormat(pattern = DateTimeFormatConstants.COMPACT_DATE_FORMAT)
    private LocalDate tradeDate;

    @Schema(description = "最高价", example = "1650.00")
    private Double highPrice;

    @Schema(description = "最低价", example = "1620.00")
    private Double lowPrice;

    @Schema(description = "收盘价", example = "1635.00")
    private Double closePrice;

    @Override
    public String toString() {
        return "DailyOhlcDTO{" +
                "windCode='" + windCode + '\'' +
                ", tradeDate=" + tradeDate +
                ", highPrice=" + highPrice +
                ", lowPrice=" + lowPrice +
                ", closePrice=" + closePrice +
                '}';
    }
}
