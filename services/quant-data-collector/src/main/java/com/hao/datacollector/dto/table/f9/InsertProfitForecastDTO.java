package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入盈利预测数据传输对象
 */
@Data
@Schema(description = "插入盈利预测数据传输对象")
public class InsertProfitForecastDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "机构评级-买入")
    private Double buyNum;

    @Schema(description = "机构评级-增持")
    private Double addGradeNum;

    @Schema(description = "机构评级-中性")
    private Double neutralNum;

    @Schema(description = "机构评级-减持")
    private Double loseNum;

    @Schema(description = "机构评级-卖出")
    private Double sellNum;

    @Schema(description = "EPS")
    private Double eps;

    @Schema(description = "净利润增长率")
    private Double profitGrowthRate;

    @Schema(description = "180天内预测机构数")
    private Integer count;

    @Schema(description = "预增数")
    private Integer addNum;

    @Schema(description = "预减数")
    private Integer reduceNum;

    @Schema(description = "7天变化率")
    private Double rateChange7;

    @Schema(description = "30天变化率")
    private Double rateChange30;

    @Schema(description = "90天变化率")
    private Double rateChange90;

    @Schema(description = "180天变化率")
    private Double rateChange180;

    @Schema(description = "最新标签")
    private String newest;

    @Schema(description = "最新数值")
    private Double newestNum;

    @Schema(description = "上月标签")
    private String lastMonth;

    @Schema(description = "上月数值")
    private Double lastMonthNum;

    @Schema(description = "一致目标价")
    private Double targetPrice;

    @Schema(description = "上涨空间")
    private Double upsideSpace;
}
