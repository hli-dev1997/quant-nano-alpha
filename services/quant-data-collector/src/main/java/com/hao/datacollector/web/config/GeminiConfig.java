package com.hao.datacollector.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Gemini API 配置类
 *
 * 职责：配置 Google Gemini API 的访问参数、HTTP 客户端，以及模型列表的加载与缓存。
 *
 * 设计目的：
 * 1. 集中管理 Gemini API 的配置参数。
 * 2. 提供 RestTemplate 用于 HTTP 调用。
 * 3. 在应用启动时加载可用模型列表，并提供刷新接口。
 *
 * 核心实现思路：
 * - 使用 @Value 注入 yaml 配置。
 * - 声明 RestTemplate Bean 供外部使用。
 * - 内部使用私有方法获取 RestTemplate，避免循环依赖。
 * - 使用 @EventListener(ApplicationReadyEvent) 在启动完成后加载模型列表。
 * - 提供 loadAvailableModels() 方法供接口调用复用。
 *
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2026-01-02
 * @description: Gemini API配置
 */
@Slf4j
@Configuration
@Getter
@Setter
public class GeminiConfig {

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemma-3-1b-it}")
    private String defaultModel;

    @Value("${ai.gemini.model:gemma-3-12b-it}")
    private String defaultImageModel;

    @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;
    
    @Value("${ai.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${ai.proxy.port:7890}")
    private int proxyPort;

    /** HTTP 超时时间（毫秒） */
    private static final int HTTP_TIMEOUT_MS = 300000;

    /** JSON 解析器（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 缓存可用模型列表
    private Set<String> availableModels = Collections.emptySet();

    // 内部缓存的 RestTemplate 实例
    private volatile RestTemplate internalRestTemplate;

    /**
     * 提供给外部使用的 RestTemplate Bean
     */
    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        return getOrCreateRestTemplate();
    }

    /**
     * 获取或创建 RestTemplate（内部使用，避免循环依赖）
     */
    private RestTemplate getOrCreateRestTemplate() {
        if (internalRestTemplate == null) {
            synchronized (this) {
                if (internalRestTemplate == null) {
                    log.info("初始化Gemini RestTemplate|Initializing_Gemini_RestTemplate,proxy={}:{}", proxyHost, proxyPort);
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
                    requestFactory.setProxy(proxy);
                    requestFactory.setConnectTimeout(HTTP_TIMEOUT_MS);
                    requestFactory.setReadTimeout(HTTP_TIMEOUT_MS);
                    internalRestTemplate = new RestTemplate(requestFactory);
                }
            }
        }
        return internalRestTemplate;
    }

    /**
     * 应用启动完成后加载模型列表
     * 使用 ApplicationReadyEvent 确保所有配置已完成注入
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        loadAvailableModels();
    }

    /**
     * 加载Gemini可用模型列表
     * 
     * 实现逻辑：
     * 1. 调用 Google API 获取模型列表。
     * 2. 解析响应并缓存模型名称（全名和简称）。
     * 3. 校验默认模型是否可用。
     * 
     * @return 模型列表原始JSON字符串
     */
    public String loadAvailableModels() {
        log.info("开始加载Gemini可用模型列表|Start_loading_Gemini_models");
        
        // 检查 API Key 是否已配置
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API Key 未配置，跳过模型列表加载|Gemini_API_Key_not_configured");
            return "{}";
        }
        
        try {
            String url = String.format("%s/models?key=%s", baseUrl, apiKey);
            log.debug("请求URL|Request_URL={}", url.replace(apiKey, "***"));
            ResponseEntity<String> response = getOrCreateRestTemplate().getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
                JsonNode modelsNode = root.path("models");

                Set<String> models = new HashSet<>();
                if (modelsNode.isArray()) {
                    for (JsonNode model : modelsNode) {
                        String name = model.path("name").asText();
                        if (name != null && !name.isEmpty()) {
                            models.add(name); // 存全名 "models/gemini-pro"
                            if (name.startsWith("models/")) {
                                models.add(name.substring(7)); // 同时存简称 "gemini-pro"
                            }
                        }
                    }
                }
                this.availableModels = models;
                log.info("Gemini模型列表加载完成|Gemini_models_loaded,count={}", models.size());

                // 校验默认模型是否可用
                if (!isModelAvailable(defaultModel)) {
                    log.warn("警告：配置的默认模型不可用|Default_model_not_available,default={},available={}",
                            defaultModel, models);
                }
                return response.getBody();
            }
            return "{}";
        } catch (Exception e) {
            log.error("加载Gemini模型列表失败，将使用空列表|Failed_to_load_Gemini_models", e);
            return "Error while loading Gemini models: " + e.getMessage();
        }
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
