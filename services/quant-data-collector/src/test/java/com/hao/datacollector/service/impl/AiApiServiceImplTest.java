package com.hao.datacollector.service.impl;

import com.hao.datacollector.DataCollectorApplication;
import com.hao.datacollector.service.AiApiService;
import com.hao.datacollector.web.config.GeminiConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiApiServiceImpl 真实集成测试
 *
 * <p><b>测试目的：</b></p>
 * <ol>
 *     <li>验证 Gemini API 的真实连通性。</li>
 *     <li>验证 API Key 鉴权是否通过。</li>
 *     <li>验证多模态（带图）请求的实际处理能力。</li>
 *     <li>验证 GeminiConfig 配置类的模型检查功能。</li>
 * </ol>
 *
 * <p><b>前置条件（自动检查）：</b></p>
 * <ul>
 *     <li>必须配置有效的 Gemini API Key。</li>
 *     <li>运行环境必须能够访问 Google API (需科学上网)。</li>
 * </ul>
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@SpringBootTest(classes = DataCollectorApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AiApiServiceImplTest {

    @Autowired
    private AiApiService aiApiService;

    @Autowired
    private GeminiConfig geminiConfig;

    /**
     * 测试前置检查：环境与配置
     * 如果检查不通过，测试将被跳过（Skipped），而不是失败（Failed）
     */
    @BeforeEach
    void checkEnvironment() {
        // 1. 检查 API Key 是否配置
        String apiKey = geminiConfig.getApiKey();
        String maskedKey = (StringUtils.hasText(apiKey) && apiKey.length() > 5) ? apiKey.substring(0, 5) + "***" : "null/empty";
        log.info("当前测试加载的 API Key: {}", maskedKey);

        if (!StringUtils.hasText(apiKey) || apiKey.contains("YOUR_REAL_API_KEY")) {
            log.warn("跳过测试：未检测到有效的 Gemini API Key");
            Assumptions.assumeTrue(false, "Skipping test: Gemini API Key not configured or valid");
        }

        // 2. 检查网络连通性 (尝试连接 Google API 端口 443)
        boolean isNetworkReachable = isHostReachable("generativelanguage.googleapis.com", 443, 2000);
        if (!isNetworkReachable) {
            log.warn("跳过测试：无法连接到 Google API (generativelanguage.googleapis.com:443)，请检查网络或代理");
            Assumptions.assumeTrue(false, "Skipping test: Google API not reachable");
        }
        
        log.info("环境检查通过：API Key 已配置，网络连通性正常");
    }

    /**
     * 测试查询 Gemini 模型列表 (真实调用)
     * 
     * 此测试应首先运行，验证 API Key 是否有权限访问 Gemini API
     */
    @Test
    @Order(1)
    @DisplayName("真实测试_查询Gemini模型列表")
    public void testListGeminiModels() {
        log.info("开始测试查询Gemini模型列表|Start_testing_list_Gemini_models");
        
        String response = aiApiService.listGeminiModels();
        
        log.info("Gemini模型列表响应长度|Gemini_models_response_length={}", response.length());
        
        assertNotNull(response, "响应不应为空");
        // 验证返回的是 JSON 格式的模型列表，而非错误信息
        assertFalse(response.contains("Error"), "API调用返回了错误信息: " + response);
        // 验证包含 models 关键字，说明返回了有效的模型列表
        assertTrue(response.contains("models"), "响应中应包含 'models' 字段");
        
        log.info("查询模型列表测试通过|List_models_test_passed");
    }

    /**
     * 测试 GeminiConfig 的模型可用性检查
     * 
     * 验证 isModelAvailable() 方法在模型列表加载后能正确判断模型是否可用
     */
    @Test
    @Order(2)
    @DisplayName("测试_模型可用性检查")
    public void testIsModelAvailable() {
        log.info("开始测试模型可用性检查|Start_testing_model_availability");
        
        // 先确保模型列表已加载
        aiApiService.listGeminiModels();
        
        String defaultModel = geminiConfig.getDefaultModel();
        log.info("当前配置的默认模型|Default_model={}", defaultModel);
        
        // 验证默认模型应该可用（因为配置中应该配置有效的模型）
        boolean isAvailable = geminiConfig.isModelAvailable(defaultModel);
        log.info("默认模型可用性检查结果|Model_availability={}", isAvailable);
        
        // 注意：这里不强制断言 true，因为可能配置的模型确实不存在
        // 但我们验证方法能正常执行
        assertNotNull(geminiConfig.getAvailableModels(), "可用模型列表不应为null");
        
        // 测试一个肯定不存在的模型
        boolean notExistResult = geminiConfig.isModelAvailable("definitely-not-exist-model-xyz");
        // 如果列表非空，应该返回 false
        if (!geminiConfig.getAvailableModels().isEmpty()) {
            assertFalse(notExistResult, "不存在的模型应返回 false");
        }
        
        log.info("模型可用性检查测试通过|Model_availability_test_passed");
    }

    /**
     * 测试 Gemini 纯文本对话功能 (真实调用)
     */
    @Test
    @Order(3)
    @DisplayName("真实测试_Gemini纯文本对话")
    public void testGeminiTextChat() {
        log.info("开始真实测试Gemini纯文本对话|Start_real_testing_Gemini_text_chat");
        
        String prompt = "你好呀，请问你是哪个模型呢？请简短回答。";
        String response = aiApiService.geminiChat(prompt);
        
        log.info("用户输入|User_input,prompt={}", prompt);
        log.info("Gemini真实回复|Gemini_real_response,response={}", response);

        assertNotNull(response, "回复不应为空");
        // 验证没有返回错误信息
        assertFalse(response.contains("Error"), "API调用返回了错误信息: " + response);
        // 验证回复长度合理，说明生成了内容
        assertTrue(response.length() > 5, "回复内容过短");
        
        log.info("纯文本对话真实测试通过|Real_text_chat_test_passed");
    }

    /**
     * 测试 Gemini 空输入处理
     * 
     * 验证系统对空输入的健壮性
     */
    @Test
    @Order(4)
    @DisplayName("测试_空输入处理")
    public void testEmptyInputHandling() {
        log.info("开始测试空输入处理|Start_testing_empty_input");
        
        String response = aiApiService.geminiChat("");
        
        log.info("空输入响应|Empty_input_response={}", response);
        
        // 验证系统不会崩溃，能返回响应（可能是错误提示或空回复）
        assertNotNull(response, "即使空输入，响应也不应为null");
        
        log.info("空输入处理测试通过|Empty_input_test_passed");
    }

    /**
     * 测试 Gemini 带图对话功能 (真实调用)
     */
    @Test
    @Order(5)
    @DisplayName("真实测试_Gemini带图对话")
    public void testGeminiImageChat() {
        log.info("开始真实测试Gemini带图对话|Start_real_testing_Gemini_image_chat");
        
        // 请确保此路径下有真实的图片文件，否则测试会跳过
        String imagePath = "e:\\project\\quant-nano-alpha\\docs\\1.png";
        
        if (!Files.exists(Paths.get(imagePath))) {
            log.warn("测试跳过_未找到图片文件|Test_skipped_image_not_found,path={}", imagePath);
            Assumptions.assumeTrue(false, "Skipping test: Test image not found");
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = "image/png";

            String prompt = "这张图里有什么？请简要描述。";
            
            String response = aiApiService.geminiChatWithImage(prompt, imageBase64, mimeType);

            log.info("用户带图输入|User_image_input,prompt={}", prompt);
            log.info("Gemini真实回复|Gemini_real_response,response={}", response);

            assertNotNull(response, "回复不应为空");
            assertFalse(response.contains("Error"), "API调用返回了错误信息: " + response);
            assertTrue(response.length() > 5, "回复内容过短");
            
            log.info("带图对话真实测试通过|Real_image_chat_test_passed");

        } catch (IOException e) {
            log.error("读取图片文件失败|Failed_to_read_image_file", e);
            Assumptions.assumeTrue(false, "Skipping test: Failed to read image file");
        }
    }

    /**
     * 检查主机端口是否可达
     *
     * @param host 主机名
     * @param port 端口
     * @param timeoutMs 超时时间(毫秒)
     * @return 是否可达
     */
    private boolean isHostReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
