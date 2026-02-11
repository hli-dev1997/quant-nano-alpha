package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入估值指标数据传输对象
 */
@Data
@Schema(description = "插入估值指标数据传输对象")
public class InsertValuationIndexDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "指标名")
    private String name;

    @Schema(description = "最新股票数据")
    private Double nowValue;

    @Schema(description = "最新行业数据")
    private Double industry;

    @Schema(description = "远期股票数据")
    private Double nowForward;

    @Schema(description = "远期行业数据")
    private Double industryForward;
}
