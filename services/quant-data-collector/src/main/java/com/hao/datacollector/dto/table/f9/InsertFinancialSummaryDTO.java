package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入成长能力数据传输对象
 */
@Data
@Schema(description = "插入成长能力数据传输对象")
public class InsertFinancialSummaryDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "年份")
    private String name;

    @Schema(description = "营业收入")
    private Double income;

    @Schema(description = "净利润")
    private Double netprofit;

    @Schema(description = "EBIT")
    private Double ebit;

    @Schema(description = "EBITDA")
    private Double ebitda;

    @Schema(description = "总资产")
    private Double asset;

    @Schema(description = "自由现金流")
    private Double freecash;
}
