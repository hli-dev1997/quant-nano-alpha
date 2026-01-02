package com.hao.datacollector.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.datacollector.service.AiApiService;
import com.hao.datacollector.web.config.GeminiConfig;
import com.openai.client.OpenAIClient;
import com.openai.errors.RateLimitException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI API 服务实现类
 *
 * 职责：实现具体的模型调用逻辑，包括 OpenAI 和 Gemini。
 *
 * 设计目的：
 * 1. 统一封装不同 AI 模型的调用细节，屏蔽底层 HTTP 交互差异。
 * 2. 提供统一的异常处理与日志记录标准。
 *
 * 为什么需要该类：
 * - 业务层不应直接依赖具体的 AI 客户端或构造 HTTP 请求。
 *
 * 核心实现思路：
 * - 注入 OpenAIClient 和 Gemini 全局配置。
 * - 使用 RestTemplate 处理 Gemini 的 REST API 调用。
 * - 统一解析响应格式，对外返回纯文本结果。
 *
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2025-12-01 14:19:49
 * @description: AiApi相关实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiApiServiceImpl implements AiApiService {

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private GeminiConfig geminiConfig;

    @Autowired
    @Qualifier("geminiRestTemplate")
    private RestTemplate geminiRestTemplate;

    @Value("${ai.openai.model}")
    private String defaultModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 OpenAI 对话接口
     *
     * 实现逻辑：
     * 1. 记录请求参数日志。
     * 2. 构建 ResponseCreateParams 请求对象。
     * 3. 调用 OpenAI SDK 发起请求。
     * 4. 记录响应结果日志。
     * 5. 捕获限流及通用异常，返回友好提示。
     *
     * @param input 用户输入
     * @return 模型回复内容
     */
    public String openAiChat(String input) {
        // 实现思路：
        // 1. 封装请求参数。
        // 2. 调用 SDK 方法。
        // 3. 异常处理与降级提示。
        try {
            log.info("调用OpenAI对话接口|Calling_OpenAI_chat_interface,model={},input={}", defaultModel, input);
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .input(input)
                    .model(defaultModel)
                    .build();

            Response response = openAIClient.responses().create(params);
            log.info("OpenAI调用成功|OpenAI_chat_success,output={}", response.output());
            return response.output().toString();

        } catch (RateLimitException e) {
            log.warn("触发OpenAI限流|OpenAI_rate_limit_hit,message={}", e.getMessage());
            return "Rate limit reached, retry later.";
        } catch (Exception e) {
            log.error("OpenAI请求失败|OpenAI_request_failed", e);
            return "Error while contacting OpenAI.";
        }
    }

    /**
     * 调用 Gemini 对话接口
     *
     * 实现逻辑：
     * 1. 获取 Gemini 全局配置。
     * 2. 组装符合 Gemini 协议的 JSON 请求体。
     * 3. 发送 HTTP POST 请求。
     * 4. 解析 JSON 响应并返回文本。
     *
     * @param input 用户输入
     * @return 模型回复内容
     */
    @Override
    public String geminiChat(String input) {
        // 实现思路：
        // 1. 构建请求 URL 与 Body。
        // 2. 使用 RestTemplate 发起调用。
        // 3. 解析结果并处理异常。
        try {
            String model = geminiConfig.getDefaultModel();
            log.info("调用Gemini对话接口|Calling_Gemini_chat_interface,model={},input={}", model, input);

            // 构建请求URL，并附带API Key
            String url = String.format("%s/models/%s:generateContent?key=%s",
                    geminiConfig.getBaseUrl(),
                    model,
                    geminiConfig.getApiKey());

            // 构建请求体
            // Gemini API格式: {"contents":[{"parts":[{"text":"..."}]}]}
            Map<String, Object> part = new HashMap<>();
            part.put("text", input);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            ResponseEntity<String> response = geminiRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 解析响应
            String responseBody = response.getBody();
            String result = parseGeminiResponse(responseBody);

            log.info("Gemini调用成功|Gemini_chat_success,output={}", result);
            return result;

        } catch (Exception e) {
            log.error("Gemini请求失败|Gemini_request_failed", e);
            return "Error while contacting Gemini: " + e.getMessage();
        }
    }

    /**
     * Call Gemini chat API / 调用Gemini聊天接口
     *
     * 实现逻辑：
     * 1. 获取模型配置与API Key。
     * 2. 构建符合Gemini协议的JSON请求体（包含文本与内联图片数据）。
     * 3. 设置HTTP头信息（Content-Type: application/json）。
     * 4. 使用RestTemplate发送POST请求。
     * 5. 解析响应JSON，提取模型生成的文本内容。
     * 6. 统一捕获异常并记录错误日志。
     *
     * @param prompt      user prompt / 用户提示词
     * @param imageBase64 image base64 string / 图片Base64字符串
     * @param mimeType    image mime type, e.g., image/jpeg / 图片类型
     * @return model reply / 模型回复
     */
    @Override
    public String geminiChatWithImage(String prompt, String imageBase64, String mimeType) {
        // 实现思路：
        // 1. 组装Gemini多模态请求体（Text + Inline Data）。
        // 2. 调用API并处理响应。
        // 3. 异常兜底处理。
        try {
            String model = geminiConfig.getDefaultImageModel();
            log.info("调用Gemini带图聊天接口|Calling_Gemini_chat_with_image,model={},prompt={}", model, prompt);

            String url = String.format("%s/models/%s:generateContent?key=%s",
                    geminiConfig.getBaseUrl(),
                    model,
                    geminiConfig.getApiKey());

            // Build request with inline data (Base64 image)
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", imageBase64);

            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("inline_data", inlineData);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(textPart, imagePart));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = geminiRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String result = parseGeminiResponse(response.getBody());
            log.info("Gemini带图调用成功|Gemini_image_chat_success,output={}", result);
            return result;

        } catch (Exception e) {
            log.error("Gemini带图请求失败|Gemini_image_request_failed", e);
            return "Error while contacting Gemini with image: " + e.getMessage();
        }
    }

    /**
     * 获取当前 API Key 可用的 Gemini 模型列表
     *
     * 实现逻辑：
     * 1. 复用 GeminiConfig 中的加载逻辑。
     * 2. 返回原始 JSON 响应，方便查看所有可用模型。
     *
     * @return 模型列表 JSON
     */
    @Override
    public String listGeminiModels() {
        log.info("调用Gemini模型列表接口|Calling_Gemini_list_models_interface");
        // 复用 GeminiConfig 中的加载逻辑，同时会刷新缓存
        return geminiConfig.loadAvailableModels();
    }

    /**
     * 解析 Gemini API 响应
     *
     * 实现逻辑：
     * 1. 解析 JSON 树结构。
     * 2. 提取 candidates[0].content.parts[0].text 字段。
     * 3. 判空处理，防止 NPE。
     *
     * Gemini响应格式:
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{"text": "..."}]
     *     }
     *   }]
     * }
     *
     * @param responseBody 响应体JSON字符串
     * @return 提取的文本内容，若无内容返回提示信息
     */
    private String parseGeminiResponse(String responseBody) {
        // 实现思路：
        // 1. 使用 Jackson 解析 JSON。
        // 2. 逐层获取 text 字段。
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }
            return "No response content from Gemini";
        } catch (Exception e) {
            log.error("解析Gemini响应失败|Failed_to_parse_Gemini_response", e);
            return responseBody;
        }
    }
}
