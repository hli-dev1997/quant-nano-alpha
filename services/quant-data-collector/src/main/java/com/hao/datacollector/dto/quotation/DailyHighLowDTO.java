package com.hao.datacollector.dto.quotation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当日最高价和最低价数据传输对象
 *
 * @author hli
 */
@Data
@Schema(description = "当日最高价和最低价数据传输对象")
public class DailyHighLowDTO {

    @Schema(description = "最高价对应的分时数据")
    private HistoryTrendDTO highPriceData;

    @Schema(description = "最低价对应的分时数据")
    private HistoryTrendDTO lowPriceData;

    @Override
    public String toString() {
        return "DailyHighLowDTO{" +
                "highPriceData=" + highPriceData +
                ", lowPriceData=" + lowPriceData +
                '}';
    }
}
