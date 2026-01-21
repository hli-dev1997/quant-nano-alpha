package util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES对称加密工具类
 *
 * 职责：提供AES密钥生成、加密与解密能力，封装统一调用入口。
 *
 * 设计目的：
 * 1. 统一AES加解密实现，避免业务侧重复拼装Cipher逻辑。
 * 2. 保持密钥处理与编码规范一致，降低使用出错概率。
 *
 * 为什么需要该类：
 * - 多处业务需要对敏感数据做对称加密，需要统一标准实现。
 *
 * 核心实现思路：
 * - 基于JCE生成密钥与Cipher实例，统一Base64编码输出。
 *
 * @author LiHao
 * @program: wstock-business-service
 * @description: AES对称加密工具类
 * @Date 2024-08-09 14:35:31
 */
public class AesEncryptUtil {

    /**
     * 加密算法
     */
    private static final String ALGORITHM = "AES";

    /**
     * 生成随机密钥
     *
     * 实现逻辑：
     * 1. 创建AES密钥生成器并设置密钥长度。
     * 2. 生成密钥并进行Base64编码输出。
     *
     * @return Base64编码的字符串密钥
     * @throws Exception 异常
     */
    public static String generateKey() throws Exception {
        // 实现思路：
        // 1. 使用KeyGenerator生成原始密钥。
        // 2. 将密钥字节转为Base64字符串。
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        //选择128位密钥
        keyGenerator.init(128);
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * 将Base64编码的密钥字符串转换为SecretKeySpec对象
     *
     * 实现逻辑：
     * 1. 解码Base64密钥字符串。
     * 2. 构造SecretKeySpec供Cipher使用。
     *
     * @param key Base64编码的密钥字符串
     * @return SecretKeySpec 对象
     */
    private static SecretKeySpec getSecretKey(String key) {
        // 实现思路：
        // 1. Base64解码密钥。
        // 2. 生成AES密钥规格对象。
        byte[] keyBytes = Base64.getDecoder().decode(key);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密数据
     *
     * 实现逻辑：
     * 1. 构建AES Cipher并初始化为加密模式。
     * 2. 执行加密并输出Base64结果。
     *
     * @param data 待加密数据
     * @param key  Base64编码的密钥字符串
     * @return 加密后的数据，使用Base64编码
     * @throws Exception 异常
     */
    public static String encrypt(String data, String key) throws Exception {
        // 实现思路：
        // 1. 初始化Cipher为加密模式。
        // 2. 对明文执行加密并Base64编码。
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKey = getSecretKey(key);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * 解密数据
     *
     * 实现逻辑：
     * 1. 构建AES Cipher并初始化为解密模式。
     * 2. 执行解密并还原明文。
     *
     * @param encryptedData 已加密的数据，Base64编码
     * @param key           Base64编码的密钥字符串
     * @return 解密后的数据
     * @throws Exception 异常
     */
    public static String decrypt(String encryptedData, String key) throws Exception {
        // 实现思路：
        // 1. 初始化Cipher为解密模式。
        // 2. 对密文解密并返回明文。
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec secretKey = getSecretKey(key);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
