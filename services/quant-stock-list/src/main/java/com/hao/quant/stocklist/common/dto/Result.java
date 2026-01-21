package com.hao.quant.stocklist.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 类说明 / Class Description:
 * 中文：通用 API 响应结果封装。
 * English: Generic API response result wrapper.
 *
 * 设计目的 / Design Purpose:
 * 中文：统一所有 API 接口的响应格式，包含状态码、消息与数据，便于前端统一解析处理。
 * English: Unify response format for all APIs with code, message and data, making it easier for frontend to parse.
 *
 * 核心实现思路 / Implementation:
 * 中文：使用泛型支持任意类型的响应数据，提供静态工厂方法快速构建成功/失败结果。
 * English: Use generics to support any response data type, provide static factory methods to build success/failure results.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 响应状态码（200 成功，其他为错误码） */
    private int code;
    /** 响应消息 */
    private String message;
    /** 响应数据 */
    private T data;

    /**
     * 构建成功结果（带数据）。
     *
     * @param data 响应数据
     * @return 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "OK", data);
    }

    /**
     * 构建成功结果（无数据）。
     *
     * @return 成功响应
     */
    public static Result<Void> success() {
        return new Result<>(200, "OK", null);
    }

    /**
     * 构建失败结果。
     *
     * @param code    错误码
     * @param message 错误消息
     * @return 失败响应
     */
    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
