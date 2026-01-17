package exception;

/**
 * 外部服务异常类
 *
 * 设计目的：
 * 1. 统一封装调用外部服务（HTTP/RPC/第三方API）时的异常。
 * 2. 便于针对外部服务异常进行重试、降级等处理。
 *
 * 实现思路：
 * - 继承 BusinessException，复用错误码机制。
 * - 通常在集成层（integration）抛出。
 *
 * @author hli
 */
public class ExternalServiceException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = 5001;

    public ExternalServiceException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ExternalServiceException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public ExternalServiceException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
