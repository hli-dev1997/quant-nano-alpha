package com.hao.datacollector.dto.replay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回放启动参数 DTO
 * <p>
 * 用于封装启动回放时的运行时参数，避免直接修改全局配置。
 *
 * @author hli
 * @date 2026-01-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayParamsDTO {

    /**
     * 回放起始日期，格式：yyyyMMdd
     */
    private String startDate;

    /**
     * 回放结束日期，格式：yyyyMMdd
     */
    private String endDate;

    /**
     * 回放速度倍数
     */
    private Integer speedMultiplier;

    /**
     * 指定股票列表（可选）
     */
    private String stockCodes;

    /**
     * 预加载时间片（分钟）
     */
    private Integer preloadMinutes;

    /**
     * 缓冲区大小
     */
    private Integer bufferMaxSize;
}
