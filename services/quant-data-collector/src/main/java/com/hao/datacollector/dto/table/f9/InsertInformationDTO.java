package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入资讯信息数据传输对象
 */
@Data
@Schema(description = "插入资讯信息数据传输对象")
public class InsertInformationDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "资讯ID", required = true)
    private String infoId;

    @Schema(description = "日期", required = true)
    private String date;

    @Schema(description = "数据来源", required = true)
    private String siteName;

    @Schema(description = "标题", required = true)
    private String title;
}
