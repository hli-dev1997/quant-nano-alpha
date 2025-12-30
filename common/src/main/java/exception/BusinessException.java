package exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

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