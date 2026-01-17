package exception;

/**
 * Redis异常类
 *
 * 设计目的：
 * 1. 统一封装Redis相关异常，如连接失败、初始化失败等。
 * 2. 便于在全局异常处理器中针对Redis异常进行降级处理。
 *
 * 实现思路：
 * - 继承 BusinessException，复用错误码机制。
 * - Redis异常通常可以降级处理，不应直接阻断业务。
 *
 * @author hli
 */
public class RedisException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = 5002;

    public RedisException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public RedisException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public RedisException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public RedisException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
