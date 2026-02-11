package com.hao.datacollector.dto.table.f9;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author LiHao
 * @description: 插入公司信息数据传输对象
 */
@Data
@Schema(description = "插入公司信息数据传输对象")
public class InsertCompanyInfoDTO {
    @Schema(description = "股票代码", required = true)
    private String windCode;

    @Schema(description = "多语言:cn.中文,en.英文", required = true)
    private String lan;

    @Schema(description = "公司名称")
    private String cpyName;

    @Schema(description = "所属行业")
    private String windIndustry;

    @Schema(description = "成立日期")
    private String inceptonDate;

    @Schema(description = "上市日期")
    private String ipoListedDate;

    @Schema(description = "注册资本")
    private String registeredcapital;

    @Schema(description = "注册资本单位")
    private String currencys;

    @Schema(description = "注册地址")
    private String registeredAddress;

    @Schema(description = "办公地址")
    private String officeAddress;

    @Schema(description = "员工总数")
    private String employeeNumbers;

    @Schema(description = "董事长")
    private String chairman;

    @Schema(description = "总经理")
    private String generalmanager;

    @Schema(description = "实际控制人")
    private String holderController;

    @Schema(description = "第一股东")
    private String holderName;

    @Schema(description = "第一股东持股比例")
    private Double holderPct;

    @Schema(description = "公司网站")
    private String webSite;
}
