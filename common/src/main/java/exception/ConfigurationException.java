package exception;

/**
 * 配置异常类
 *
 * 设计目的：
 * 1. 统一封装配置相关异常，如配置格式错误、必要配置缺失等。
 * 2. 便于在应用启动或运行时快速定位配置问题。
 *
 * 实现思路：
 * - 继承 BusinessException，复用错误码机制。
 * - 通常在应用启动阶段或配置加载时抛出。
 *
 * @author hli
 */
public class ConfigurationException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = 4002;

    public ConfigurationException(String message) {
        super(DEFAULT_ERROR_CODE, message);
    }

    public ConfigurationException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(DEFAULT_ERROR_CODE, message, cause);
    }

    public ConfigurationException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
