package com.hao.riskcontrol.dto.quotation;

import com.fasterxml.jackson.annotation.JsonFormat;
import constants.DateTimeFormatConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 指数行情数据传输对象
 * <p>
 * 用于接收 Kafka 推送的指数行情数据。
 * 字段与 HistoryTrendDTO 对齐，便于统一处理。
 *
 * @author hli
 * @date 2026-01-18
 */
@Data
@Schema(description = "指数行情数据传输对象")
public class IndexQuotationDTO {

    @Schema(description = "指数代码", example = "000300.SH")
    private String windCode;

    @Schema(description = "交易日期时间", example = "2026-01-18 13:01:01")
    @JsonFormat(pattern = DateTimeFormatConstants.DEFAULT_DATETIME_FORMAT)
    private LocalDateTime tradeDate;

    @Schema(description = "最新价", example = "3850.25")
    private Double latestPrice;

    @Schema(description = "总成交量", example = "1234567890")
    private Double totalVolume;

    @Schema(description = "均价/总成交额", example = "3845.50")
    private Double averagePrice;
}
