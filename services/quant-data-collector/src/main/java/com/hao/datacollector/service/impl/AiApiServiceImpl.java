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

import java.util.Base64;
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
        // 2. 调用API并处理响应，带重试机制。
        // 3. 异常兜底处理。
        
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

        // 重试机制：最多重试3次
        int maxRetries = 3;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("图片请求尝试|Image_request_attempt={}/{}", attempt, maxRetries);
                
                ResponseEntity<String> response = geminiRestTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        String.class
                );

                String result = parseGeminiResponse(response.getBody());
                log.info("Gemini带图调用成功|Gemini_image_chat_success,attempt={},output={}", attempt, result);
                return result;
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini带图请求失败(尝试{}/{})|Gemini_image_request_failed,attempt={},error={}", 
                        attempt, maxRetries, attempt, e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        // 重试前等待1秒
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("Gemini带图请求最终失败|Gemini_image_request_finally_failed", lastException);
        return "Error while contacting Gemini with image: " + 
                (lastException != null ? lastException.getMessage() : "Unknown error");
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

    /**
     * 调用 Gemini 音频理解接口
     *
     * 实现逻辑：
     * 1. 使用 gemma-3n-e4b-it 模型配置。
     * 2. 构建符合 Gemini 多模态协议的 JSON 请求体（包含文本与内联音频数据）。
     * 3. 设置 HTTP 头信息（Content-Type: application/json）。
     * 4. 使用 RestTemplate 发送 POST 请求，带重试机制。
     * 5. 解析响应 JSON，提取模型生成的文本内容。
     *
     * @param prompt      用户提示词
     * @param audioBase64 音频Base64字符串
     * @param mimeType    音频类型，如 audio/wav, audio/mp3, audio/mpeg
     * @return 模型回复
     */
    @Override
    public String geminiChatWithAudio(String prompt, String audioBase64, String mimeType) {
        // 实现思路：
        // 1. 先通过 Files API 上传音频文件获取 file URI。
        // 2. 使用 file_data 引用上传的文件（而非 inline_data）。
        // 3. 调用 generateContent API 进行音频理解。
        // 4. 带重试机制进行异常兜底处理。

        String model = geminiConfig.getDefaultAudioVideoModel();
        log.info("调用Gemini音频理解接口|Calling_Gemini_audio_chat,model={},prompt={}", model, prompt);

        // 重试机制：最多重试3次
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("音频请求尝试|Audio_request_attempt={}/{}", attempt, maxRetries);

                // 步骤1：上传音频文件到 Files API
                String fileUri = uploadFileToGemini(audioBase64, mimeType, "audio");
                if (fileUri == null || fileUri.startsWith("Error")) {
                    log.warn("音频文件上传失败|Audio_file_upload_failed,response={}", fileUri);
                    // 如果上传失败，回退到 inline_data 方式尝试
                    return callWithInlineData(prompt, audioBase64, mimeType, model, "audio");
                }

                // 步骤2：使用 file_data 引用方式调用 generateContent
                String url = String.format("%s/models/%s:generateContent?key=%s",
                        geminiConfig.getBaseUrl(),
                        model,
                        geminiConfig.getApiKey());

                // 构建请求体，使用 file_data 引用上传的文件
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("file_uri", fileUri);
                fileData.put("mime_type", mimeType);

                Map<String, Object> audioPart = new HashMap<>();
                audioPart.put("file_data", fileData);

                Map<String, Object> textPart = new HashMap<>();
                textPart.put("text", prompt);

                Map<String, Object> content = new HashMap<>();
                content.put("parts", List.of(textPart, audioPart));

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
                log.info("Gemini音频理解调用成功|Gemini_audio_chat_success,attempt={},output={}", attempt, result);
                return result;

            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini音频请求失败(尝试{}/{})|Gemini_audio_request_failed,attempt={},error={}",
                        attempt, maxRetries, attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        // 重试前等待1秒
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Gemini音频请求最终失败|Gemini_audio_request_finally_failed", lastException);
        return "Error while contacting Gemini with audio: " +
                (lastException != null ? lastException.getMessage() : "Unknown error");
    }

    /**
     * 上传文件到 Google Files API
     *
     * 实现逻辑：
     * 1. 调用 media.upload 端点上传 Base64 解码后的文件数据。
     * 2. 返回文件的 URI 供后续 generateContent 使用。
     *
     * @param dataBase64 文件 Base64 编码数据
     * @param mimeType   文件 MIME 类型
     * @param fileType   文件类型描述（用于日志）
     * @return 上传后的文件 URI，失败返回错误信息
     */
    private String uploadFileToGemini(String dataBase64, String mimeType, String fileType) {
        try {
            // Google Files API 上传端点
            String uploadUrl = String.format("%s/files?key=%s",
                    geminiConfig.getBaseUrl().replace("/v1beta", "/upload/v1beta"),
                    geminiConfig.getApiKey());

            // 解码 Base64 数据
            byte[] fileBytes = Base64.getDecoder().decode(dataBase64);

            // 构建 multipart 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf(mimeType));
            headers.set("X-Goog-Upload-Protocol", "raw");

            HttpEntity<byte[]> entity = new HttpEntity<>(fileBytes, headers);

            ResponseEntity<String> response = geminiRestTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            // 解析响应获取文件 URI
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode fileNode = root.path("file");
                String fileUri = fileNode.path("uri").asText();
                log.info("文件上传成功|File_upload_success,type={},uri={}", fileType, fileUri);
                return fileUri;
            }
            return "Error: Upload failed with status " + response.getStatusCode();

        } catch (Exception e) {
            log.warn("文件上传异常|File_upload_exception,type={},error={}", fileType, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 使用 inline_data 方式调用（作为 file_data 方式的回退）
     *
     * @param prompt     用户提示词
     * @param dataBase64 文件 Base64 数据
     * @param mimeType   MIME 类型
     * @param model      模型名称
     * @param dataType   数据类型（audio/video）
     * @return API 响应
     */
    private String callWithInlineData(String prompt, String dataBase64, String mimeType, String model, String dataType) {
        try {
            String url = String.format("%s/models/%s:generateContent?key=%s",
                    geminiConfig.getBaseUrl(),
                    model,
                    geminiConfig.getApiKey());

            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mime_type", mimeType);
            inlineData.put("data", dataBase64);

            Map<String, Object> dataPart = new HashMap<>();
            dataPart.put("inline_data", inlineData);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(textPart, dataPart));

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

            return parseGeminiResponse(response.getBody());

        } catch (Exception e) {
            log.error("inline_data方式调用失败|Inline_data_call_failed,type={},error={}", dataType, e.getMessage());
            return "Error with inline data: " + e.getMessage();
        }
    }

    /**
     * 调用 Gemini 视频理解接口
     *
     * 实现逻辑：
     * 1. 先通过 Files API 上传视频文件获取 file URI。
     * 2. 使用 file_data 引用上传的文件（而非 inline_data）。
     * 3. 调用 generateContent API 进行视频理解。
     * 4. 带重试机制进行异常兜底处理。
     *
     * @param prompt      用户提示词
     * @param videoBase64 视频Base64字符串
     * @param mimeType    视频类型，如 video/mp4, video/webm
     * @return 模型回复
     */
    @Override
    public String geminiChatWithVideo(String prompt, String videoBase64, String mimeType) {
        // 实现思路：
        // 1. 先上传视频到 Files API。
        // 2. 使用 file_data 引用方式调用 generateContent。
        // 3. 带重试机制进行异常兜底处理。

        String model = geminiConfig.getDefaultAudioVideoModel();
        log.info("调用Gemini视频理解接口|Calling_Gemini_video_chat,model={},prompt={}", model, prompt);

        // 重试机制：最多重试3次（视频文件较大，网络抖动更常见）
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("视频请求尝试|Video_request_attempt={}/{}", attempt, maxRetries);

                // 步骤1：上传视频文件到 Files API
                String fileUri = uploadFileToGemini(videoBase64, mimeType, "video");
                if (fileUri == null || fileUri.startsWith("Error")) {
                    log.warn("视频文件上传失败|Video_file_upload_failed,response={}", fileUri);
                    // 如果上传失败，回退到 inline_data 方式尝试
                    return callWithInlineData(prompt, videoBase64, mimeType, model, "video");
                }

                // 步骤2：使用 file_data 引用方式调用 generateContent
                String url = String.format("%s/models/%s:generateContent?key=%s",
                        geminiConfig.getBaseUrl(),
                        model,
                        geminiConfig.getApiKey());

                // 构建请求体，使用 file_data 引用上传的文件
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("file_uri", fileUri);
                fileData.put("mime_type", mimeType);

                Map<String, Object> videoPart = new HashMap<>();
                videoPart.put("file_data", fileData);

                Map<String, Object> textPart = new HashMap<>();
                textPart.put("text", prompt);

                Map<String, Object> content = new HashMap<>();
                content.put("parts", List.of(textPart, videoPart));

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
                log.info("Gemini视频理解调用成功|Gemini_video_chat_success,attempt={},output={}", attempt, result);
                return result;

            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini视频请求失败(尝试{}/{})|Gemini_video_request_failed,attempt={},error={}",
                        attempt, maxRetries, attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        // 重试前等待1.5秒（视频处理时间较长）
                        Thread.sleep(1500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Gemini视频请求最终失败|Gemini_video_request_finally_failed", lastException);
        return "Error while contacting Gemini with video: " +
                (lastException != null ? lastException.getMessage() : "Unknown error");
    }
}
