package com.hao.datacollector.web.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.Set;

/**
 * Gemini API 配置类
 *
 * 职责：配置 Google Gemini API 的访问参数和 HTTP 客户端。
 *
 * 设计目的：
 * 1. 集中管理 Gemini API 的配置参数。
 * 2. 提供 RestTemplate 用于 HTTP 调用。
 *
 * 核心实现思路：
 * - 使用 @Value 注入 yaml 配置。
 * - 声明 RestTemplate Bean。
 * - 模型列表的加载和缓存由 GeminiModelCacheInitializer 负责。
 *
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2026-01-02
 * @description: Gemini API配置
 */
@Slf4j
@Configuration
@Getter
@Setter // 添加 Setter 以便其他 Bean 可以更新缓存
public class GeminiConfig {

    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.0-flash-lite-001}")
    private String defaultModel;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;
    
    @Value("${ai.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${ai.proxy.port:7890}")
    private int proxyPort;

    // 缓存可用模型列表
    private Set<String> availableModels = Collections.emptySet();

    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        log.info("初始化Gemini RestTemplate|Initializing_Gemini_RestTemplate,proxy={}:{}", proxyHost, proxyPort);
        
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        requestFactory.setProxy(proxy);
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(30000);
        
        return new RestTemplate(requestFactory);
    }
    
    /**
     * 检查模型是否可用
     * @param modelName 模型名称
     * @return true if available
     */
    public boolean isModelAvailable(String modelName) {
        if (availableModels.isEmpty()) {
            // 如果加载失败，默认放行，避免误杀
            log.warn("可用模型列表为空，跳过检查|Available_models_list_is_empty_skipping_check,model={}", modelName);
            return true;
        }
        return availableModels.contains(modelName) || availableModels.contains("models/" + modelName);
    }
}
