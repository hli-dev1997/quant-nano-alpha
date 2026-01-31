package com.hao.riskcontrol.dto.sentiment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市场情绪数据传输对象
 * <p>
 * 用于推送到 Redis 供前端仪表盘展示。
 * 包含百分制得分、原始涨跌幅、区间名称、操作建议等完整信息。
 *
 * @author hli
 * @date 2026-01-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "市场情绪数据传输对象")
public class MarketSentimentDTO {

    /**
     * 百分制得分（0-100）
     * <p>
     * 75-100：强势区，50-74：震荡偏强，25-49：震荡偏弱，0-24：弱势区
     */
    @Schema(description = "百分制得分", example = "75")
    private Integer score;

    /**
     * 原始综合涨跌幅（整数基点）
     * <p>
     * 例如：150 表示综合涨跌 +1.5%，-98 表示综合涨跌 -0.98%
     */
    @Schema(description = "原始综合涨跌幅（基点）", example = "-98")
    private Integer rawChange;

    /**
     * 市场区间名称
     * <p>
     * 可选值：强势区、震荡偏强区、震荡偏弱区、弱势区
     */
    @Schema(description = "市场区间名称", example = "弱势区")
    private String zoneName;

    /**
     * 操作建议
     * <p>
     * 例如：积极进攻、持仓或优化、防御减仓、严格风控
     */
    @Schema(description = "操作建议", example = "严格风控")
    private String suggestion;

    /**
     * 更新时间戳（毫秒）
     */
    @Schema(description = "更新时间戳", example = "1706716800000")
    private Long timestamp;

    /**
     * 格式化的涨跌幅显示
     * <p>
     * 例如："+1.50%" 或 "-0.98%"
     */
    @Schema(description = "格式化涨跌幅", example = "-0.98%")
    private String formattedChange;
}
