package com.hao.datacollector.core.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 表类型枚举
 * <p>
 * 定义冷热表的类型和对应的物理表名。
 * <ul>
 *   <li>HOT: 热表，存储 2024-01-01 及之后的数据</li>
 *   <li>WARM: 温表，存储 2024-01-01 之前的数据</li>
 * </ul>
 *
 * @author hli
 * @date 2026-02-04
 */
@Getter
@AllArgsConstructor
public enum TableType {

    /**
     * 热表：存储近期数据（2024-01-01 及之后）
     */
    HOT("tb_quotation_history_hot"),

    /**
     * 温表：存储历史数据（2024-01-01 之前）
     */
    WARM("tb_quotation_history_warm");

    /**
     * 物理表名
     */
    private final String tableName;
}
