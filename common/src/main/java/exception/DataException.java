package exception;

/**
 * 数据异常类
 *
 * 设计目的：
 * 1. 统一封装数据层相关异常，如数据解析失败、数据格式错误、数据为空等。
 * 2. 便于在全局异常处理器中针对数据异常进行特定处理。
 *
 * 实现思路：
 * - 继承 BusinessException，复用错误码机制。
 * - 提供便捷构造函数，减少调用方代码量。
 *
 * @author hli
 */
public class DataException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = 4001;

    public DataException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public DataException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public DataException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public DataException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
