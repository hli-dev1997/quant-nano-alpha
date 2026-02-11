package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入关键统计数据传输对象
 */
@Data
@Schema(description = "插入关键统计数据传输对象")
public class InsertKeyStatisticsDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "总市值")
    private Double totalValue;

    @Schema(description = "PE(TTM)")
    private Double peT;

    @Schema(description = "PE")
    private Double pe;

    @Schema(description = "PB(MRQ)")
    private Double pb;

    @Schema(description = "PS(TTM)")
    private Double ps;

    @Schema(description = "归母净利润(TTM)")
    private Double ttm;

    @Schema(description = "Beta(100周)")
    private Double beta;

    @Schema(description = "总股本")
    private Double generalCapital;

    @Schema(description = "BPS(LF)")
    private Double bps;

    @Schema(description = "EPS(TTM)")
    private Double eps;

    @Schema(description = "预测EPS(平均)")
    private Double forecastEps;

    @Schema(description = "营业总收入(TTM)")
    private Double grossRevenue;

    @Schema(description = "扣非后净利润(TTM)")
    private Double retainedProfits;

    @Schema(description = "一致目标价")
    private Double targetPrice;
}
