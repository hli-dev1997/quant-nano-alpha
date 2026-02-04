package com.hao.datacollector.core.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 查询分段对象
 * <p>
 * 表示一个跨表查询中的单个分段，包含表类型和时间范围。
 * TableRouter 根据查询时间范围生成一个或多个 QuerySegment。
 *
 * @author hli
 * @date 2026-02-04
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuerySegment {

    /**
     * 表类型（HOT/WARM）
     */
    private TableType tableType;

    /**
     * 分段起始日期
     */
    private LocalDate startDate;

    /**
     * 分段结束日期
     */
    private LocalDate endDate;
}
