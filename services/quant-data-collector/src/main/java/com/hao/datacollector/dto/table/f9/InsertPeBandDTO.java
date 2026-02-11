package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入PE估值带数据传输对象
 */
@Data
@Schema(description = "插入PE估值带数据传输对象")
public class InsertPeBandDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "日期")
    private String date;

    @Schema(description = "收盘价")
    private Double closePrice;

    @Schema(description = "指标值(EPS/BPS)")
    private Double indicatorValue;

    @Schema(description = "复权因子")
    private Double adjustmentFactor;

    @Schema(description = "DR代表股份数")
    private Double drShareRatio;
}
