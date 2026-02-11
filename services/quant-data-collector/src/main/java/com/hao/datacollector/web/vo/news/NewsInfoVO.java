package com.hao.datacollector.web.vo.news;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * @author hli
 * @Date 2025-06-18 16:46:35
 * @description: 新闻数据VO对象
 */
@Data
@Schema(name = "新闻数据传输对象")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NewsInfoVO {

    @JSONField(format = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(description = "发布日期", required = true)
    private LocalDate date;

    @Schema(description = "新闻ID", required = true)
    private String id;

    @Schema(description = "站点名称", required = true)
    private String sitename;

    @Schema(description = "标题", required = true)
    private String title;
}