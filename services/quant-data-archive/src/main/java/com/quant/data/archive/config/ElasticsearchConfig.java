package com.quant.data.archive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.lang.NonNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Elasticsearch 客户端配置类
 *
 * 设计目的：
 * 1. 配置 ES 连接参数（地址、认证、SSL）
 * 2. 解决自签名证书信任问题
 *
 * 为什么需要该类：
 * - 本地 ES 使用 HTTPS 自签名证书，Java 默认不信任
 * - 需要自定义 SSL Context 来信任本地证书
 *
 * 注意：信任所有证书仅限开发环境使用！
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.username:elastic}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    /**
     * 配置 ES 客户端连接
     *
     * 实现逻辑：
     * 1. 指定 ES 服务器地址
     * 2. 开启 SSL 并信任自签名证书
     * 3. 配置 Basic Auth 认证
     *
     * @return ES 客户端配置
     */
    @Override
    @NonNull
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo("localhost:9200")
                .usingSsl(buildTrustAllSslContext())
                .withBasicAuth(username, password)
                .build();
    }

    /**
     * 构建信任所有证书的 SSL Context
     *
     * 实现逻辑：
     * 1. 创建空实现的 TrustManager
     * 2. 初始化 SSL Context
     *
     * 注意：仅限开发环境使用，生产环境应使用正规证书
     *
     * @return SSL Context
     */
    private SSLContext buildTrustAllSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { 
                        return new X509Certificate[0]; 
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // 信任所有客户端证书（开发环境）
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // 信任所有服务端证书（开发环境）
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("配置 SSL 失败|SSL_config_failed", e);
        }
    }
}
