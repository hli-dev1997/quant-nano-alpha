package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入公告数据传输对象
 */
@Data
@Schema(description = "插入公告数据传输对象")
public class InsertNoticeDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "发布日期")
    private String date;

    @Schema(description = "公告标题")
    private String title;
}
