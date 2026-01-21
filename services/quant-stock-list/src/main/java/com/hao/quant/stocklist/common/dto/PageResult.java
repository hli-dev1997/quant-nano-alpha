package com.hao.quant.stocklist.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 类说明 / Class Description:
 * 中文：分页结果通用封装对象。
 * English: Generic pagination result wrapper object.
 *
 * 设计目的 / Design Purpose:
 * 中文：统一封装分页元数据与记录列表，便于各接口返回一致的分页响应格式。
 * English: Unify pagination metadata and record list to provide consistent pagination response format across APIs.
 *
 * 核心实现思路 / Implementation:
 * 中文：使用泛型支持任意类型的记录列表，提供静态工厂方法快速构建空结果。
 * English: Use generics to support any record type, provide static factory methods to quickly build empty results.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总记录数 */
    private long total;
    /** 当前页码 */
    private int pageNum;
    /** 每页大小 */
    private int pageSize;
    /** 当前页记录列表 */
    private List<T> records;

    /**
     * 构建指定分页参数的空结果。
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return PageResult.<T>builder()
                .total(0)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .records(Collections.emptyList())
                .build();
    }

    /**
     * 构建默认空分页结果（页码1，每页20条）。
     *
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty() {
        return empty(1, 20);
    }
}
