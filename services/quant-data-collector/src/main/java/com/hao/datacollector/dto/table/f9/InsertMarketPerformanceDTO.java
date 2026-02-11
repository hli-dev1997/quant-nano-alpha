package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入市场表现数据传输对象
 */
@Data
@Schema(description = "插入市场表现数据传输对象")
public class InsertMarketPerformanceDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "股票名称")
    private String windName;

    @Schema(description = "当前股价")
    private Double value;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "涨跌幅(价格)")
    private Double limitAmount;

    @Schema(description = "涨跌幅(百分比)")
    private Double limitPrice;

    @Schema(description = "区间最高价")
    private Double topAmount;

    @Schema(description = "区间最低价")
    private Double lowAmount;

    @Schema(description = "年迄今涨跌幅")
    private Double toThisDayPriceLimit;

    @Schema(description = "近一月涨跌幅")
    private Double oneMonthPriceLimit;

    @Schema(description = "近三月涨跌幅")
    private Double threeMonthPriceLimit;

    @Schema(description = "近一年涨跌幅")
    private Double oneYearPriceLimit;

    @Schema(description = "融资融券余额")
    private Double financeAmount;

    @Schema(description = "区间起始时间")
    private String startTime;

    @Schema(description = "区间结束时间")
    private String endTime;

    @Schema(description = "日均成交")
    private Double averageDaily;
}
