package exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常类
 *
 * 设计目的：
 * 1. 统一封装业务层可预期的异常，与系统异常区分处理。
 * 2. 携带错误码，便于前端/调用方进行精确的异常处理和用户提示。
 * 3. 支持异常链，保留原始异常信息便于问题排查。
 *
 * 实现思路：
 * - 继承 RuntimeException，避免方法签名污染。
 * - 通过 errorCode 区分不同的业务错误类型。
 * - 同时保留 message 字段和父类 message，确保兼容性。
 *
 * @author hli
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    /**
     * 错误码
     */
    private Integer errorCode;

    /**
     * 消息内容
     */
    private String message;

    public BusinessException(Integer errorCode, String message) {
        super(message);  // 传递给父类，确保 getMessage() 正常工作
        this.message = message;
        this.errorCode = errorCode;
    }

    /**
     * 支持异常链的构造函数
     *
     * @param errorCode 错误码
     * @param message   消息内容
     * @param cause     原始异常
     */
    public BusinessException(Integer errorCode, String message, Throwable cause) {
        super(message, cause);
        this.message = message;
        this.errorCode = errorCode;
    }
}