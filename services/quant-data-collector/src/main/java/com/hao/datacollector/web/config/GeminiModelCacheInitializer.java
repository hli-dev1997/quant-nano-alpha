package com.hao.datacollector.web.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Gemini 模型缓存初始化器
 *
 * 职责：在应用启动后，动态加载并缓存可用的 Gemini 模型列表。
 *
 * 设计目的：
 * 1. 解决 GeminiConfig 中的循环依赖问题。
 * 2. 将配置与初始化逻辑分离，遵循单一职责原则。
 *
 * 核心实现思路：
 * - 作为一个独立的组件，在 GeminiConfig 和 RestTemplate 都被创建后执行。
 * - 使用 @PostConstruct 注解，在依赖注入完成后调用 Google API。
 * - 将获取到的模型列表设置回 GeminiConfig 的缓存中。
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Configuration
public class GeminiModelCacheInitializer {

    @Autowired
    private GeminiConfig geminiConfig;

    @Autowired
    @Qualifier("geminiRestTemplate")
    private RestTemplate restTemplate;

    @PostConstruct
    public void initialize() {
        log.info("开始加载Gemini可用模型列表|Start_loading_Gemini_models");
        try {
            String url = String.format("%s/models?key=%s", geminiConfig.getBaseUrl(), geminiConfig.getApiKey());
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
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
                geminiConfig.setAvailableModels(models); // 更新到配置类
                log.info("Gemini模型列表加载完成|Gemini_models_loaded,count={},models={}", models.size(), models);

                // 校验默认模型是否可用
                if (!geminiConfig.isModelAvailable(geminiConfig.getDefaultModel())) {
                    log.warn("警告：配置的默认模型不可用|Default_model_not_available,default={},available={}",
                            geminiConfig.getDefaultModel(), models);
                }
            }
        } catch (Exception e) {
            log.error("加载Gemini模型列表失败，将使用空列表|Failed_to_load_Gemini_models", e);
            // 不抛出异常，避免阻断服务启动
        }
    }
}
