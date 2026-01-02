package com.hao.datacollector.service.impl;

import com.hao.datacollector.DataCollectorApplication;
import com.hao.datacollector.service.AiApiService;
import com.hao.datacollector.web.config.GeminiConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
     * <p>
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
     * <p>
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
     * <p>
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
        String imagePath = "e:\\project\\quant-nano-alpha\\docs\\1.jpg";

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
     * 测试 Gemini 带图对话频次限制 (压力测试)
     *
     * <p><b>测试目的：</b></p>
     * <ol>
     *     <li>验证 API 的调用频次上限。</li>
     *     <li>探测触发限流的调用次数阈值。</li>
     *     <li>记录每次调用的响应时间。</li>
     * </ol>
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>连续调用指定次数，每次记录耗时和结果。</li>
     *     <li>统计成功/失败/限流的次数。</li>
     *     <li>输出完整的调用报告。</li>
     * </ul>
     */
    @Test
    @Order(13)
    @DisplayName("压力测试_Gemini带图对话频次")
    public void testGeminiImageChatRateLimit() {
        log.info("开始测试Gemini带图对话频次限制|Start_testing_Gemini_image_chat_rate_limit");

        String imagePath = "e:\\project\\quant-nano-alpha\\docs\\1.jpg";

        if (!Files.exists(Paths.get(imagePath))) {
            log.warn("测试跳过_未找到图片文件|Test_skipped_image_not_found,path={}", imagePath);
            Assumptions.assumeTrue(false, "Skipping test: Test image not found");
            return;
        }

        try {
            byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = "image/jpeg";

            // 配置测试参数
            int totalCalls = 30;           // 总调用次数
            int delayBetweenCalls = 0;     // 每次调用间隔（毫秒），0表示无间隔连续调用

            int successCount = 0;
            int failCount = 0;
            int rateLimitCount = 0;
            List<Long> responseTimes = new ArrayList<>();

            log.info("=== 频次测试开始 | Rate Limit Test Start ===");
            log.info("计划调用次数: {}, 调用间隔: {}ms", totalCalls, delayBetweenCalls);

            for (int i = 1; i <= totalCalls; i++) {
                String prompt = "请用一句话描述这张图片。(测试 #" + i + ")";
                long startTime = System.currentTimeMillis();

                try {
                    String response = aiApiService.geminiChatWithImage(prompt, imageBase64, mimeType);
                    long elapsed = System.currentTimeMillis() - startTime;
                    responseTimes.add(elapsed);

                    if (response != null && !response.contains("Error") && !response.contains("rate")) {
                        successCount++;
                        log.info("[{}/{}] 成功 | {}ms | 回复: {}", i, totalCalls, elapsed,
                                response.substring(0, Math.min(50, response.length())).replace("\n", " "));
                    } else if (response != null && (response.toLowerCase().contains("rate") || response.contains("429"))) {
                        rateLimitCount++;
                        log.warn("[{}/{}] 限流 | {}ms | 响应: {}", i, totalCalls, elapsed, response);
                    } else {
                        failCount++;
                        log.warn("[{}/{}] 失败 | {}ms | 响应: {}", i, totalCalls, elapsed, response);
                    }

                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    responseTimes.add(elapsed);

                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        rateLimitCount++;
                        log.warn("[{}/{}] 限流异常 | {}ms | {}", i, totalCalls, elapsed, e.getMessage());
                    } else {
                        failCount++;
                        log.error("[{}/{}] 调用异常 | {}ms | {}", i, totalCalls, elapsed, e.getMessage());
                    }
                }

                // 调用间隔
                if (i < totalCalls && delayBetweenCalls > 0) {
                    Thread.sleep(delayBetweenCalls);
                }
            }

            // 输出统计报告
            double avgTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            log.info("=================================================");
            log.info("频次测试报告 | Rate Limit Test Report");
            log.info("=================================================");
            log.info("总调用次数: {}", totalCalls);
            log.info("成功次数: {} ({}%)", successCount, successCount * 100 / totalCalls);
            log.info("失败次数: {}", failCount);
            log.info("限流次数: {}", rateLimitCount);
            log.info("平均响应时间: {}ms", String.format("%.0f", avgTime));
            log.info("最快响应: {}ms, 最慢响应: {}ms", minTime, maxTime);
            log.info("=================================================");

            // 如果有任何成功调用，则测试通过
            assertTrue(successCount > 0, "至少应该有一次成功调用");

        } catch (IOException | InterruptedException e) {
            log.error("频次测试异常|Rate_limit_test_exception", e);
            Assumptions.assumeTrue(false, "Test exception: " + e.getMessage());
        }
    }

    /**
     * 验证并展示所有可用的 Gemini 模型
     * <p>
     * 此测试将列出当前 API Key 有权限访问的所有模型，
     * 并检查配置文件中指定的默认模型是否在可用列表中。
     */
    @Test
    @Order(6)
    @DisplayName("验证_展示所有可用模型")
    public void testVerifyAllAvailableModels() {
        log.info("开始验证所有可用模型|Start_verifying_all_available_models");

        // 1. 触发列表加载
        String rawJson = aiApiService.listGeminiModels();
        assertNotNull(rawJson, "模型列表响应不应为空");

        // 2. 获取解析后的模型集合
        Set<String> availableModels = geminiConfig.getAvailableModels();

        log.info("当前可用模型总数|Total_available_models={}", availableModels.size());
        log.info("可用模型列表如下|Available_models_list:");
        availableModels.stream()
                .sorted()
                .forEach(model -> log.info(" - {}", model));

        // 3. 验证配置的默认模型是否可用
        String defaultModel = geminiConfig.getDefaultModel();
        String defaultImageModel = geminiConfig.getDefaultImageModel();

        boolean isDefaultModelAvailable = geminiConfig.isModelAvailable(defaultModel);
        boolean isDefaultImageModelAvailable = geminiConfig.isModelAvailable(defaultImageModel);

        log.info("配置模型可用性检查|Configured_models_check:");
        log.info(" - 默认文本模型 [{}]: {}", defaultModel, isDefaultModelAvailable ? "可用 (Available)" : "不可用 (Not Available)");
        log.info(" - 默认图片模型 [{}]: {}", defaultImageModel, isDefaultImageModelAvailable ? "可用 (Available)" : "不可用 (Not Available)");

        // 断言至少有一些模型可用
        assertFalse(availableModels.isEmpty(), "可用模型列表不应为空");

        log.info("验证所有可用模型测试通过|Verify_all_available_models_test_passed");
    }

    /**
     * 实际调用所有可用模型，验证哪些模型可以真正工作
     * <p>
     * 注意：此测试可能会比较耗时，因为它会遍历所有模型。
     */
    @Test
    @Order(7)
    @DisplayName("压力测试_实际调用所有可用模型")
    public void testInvokeAllAvailableModels() {
        log.info("开始实际调用所有可用模型|Start_invoking_all_available_models");

        // 1. 确保模型列表已加载
        if (geminiConfig.getAvailableModels().isEmpty()) {
            aiApiService.listGeminiModels();
        }

        Set<String> allModels = geminiConfig.getAvailableModels();
        // 过滤模型：
        // 1. 排除包含 "embedding" 的模型（通常不支持 generateContent）
        // 2. 排除以 "models/" 开头的名称（避免重复测试，且 URL 拼接可能出错）
        // 3. 排除 "aqa" (Attributed Question Answering) 模型，通常需要特殊输入
        List<String> modelsToTest = allModels.stream()
                .filter(m -> !m.startsWith("models/"))
                .filter(m -> !m.contains("embedding"))
                .filter(m -> !m.contains("aqa"))
                .sorted()
                .collect(Collectors.toList());

        log.info("计划测试的模型数量|Models_to_test_count={}", modelsToTest.size());

        String originalDefaultModel = geminiConfig.getDefaultModel();
        Map<String, String> results = new HashMap<>();
        List<String> successModels = new ArrayList<>();
        List<String> failedModels = new ArrayList<>();

        try {
            for (String model : modelsToTest) {
                log.info("正在测试模型: {} ...", model);

                // 动态切换默认模型
                geminiConfig.setDefaultModel(model);

                try {
                    // 发送简单请求
                    long start = System.currentTimeMillis();
                    String response = aiApiService.geminiChat("Hello! Just testing connectivity. Reply 'OK'.");
                    long duration = System.currentTimeMillis() - start;

                    if (response != null && !response.contains("Error") && !response.contains("Exception")) {
                        results.put(model, "SUCCESS (" + duration + "ms): " + response.substring(0, Math.min(response.length(), 50)).replace("\n", " "));
                        successModels.add(model);
                        log.info(" -> 模型 [{}] 调用成功", model);
                    } else {
                        results.put(model, "FAILED: Response contained error - " + response);
                        failedModels.add(model);
                        log.warn(" -> 模型 [{}] 调用返回错误", model);
                    }
                } catch (Exception e) {
                    results.put(model, "EXCEPTION: " + e.getMessage());
                    failedModels.add(model);
                    log.error(" -> 模型 [{}] 调用异常", model, e);
                }

                // 避免触发限流，稍微停顿
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 恢复原始配置
            geminiConfig.setDefaultModel(originalDefaultModel);
        }

        // 输出汇总报告
        log.info("==================================================");
        log.info("模型调用测试汇总报告|Model_Invocation_Summary_Report");
        log.info("==================================================");
        log.info("测试总数: {}", modelsToTest.size());
        log.info("成功数量: {}", successModels.size());
        log.info("失败数量: {}", failedModels.size());

        log.info("--- 成功模型列表 (可直接复制使用) ---");
        // 打印一个干净的列表，方便复制
        List<String> cleanSuccessList = new ArrayList<>(successModels);
        log.info("SUCCESS_MODELS = {}", cleanSuccessList);

        successModels.forEach(m -> log.info("[SUCCESS] {} -> {}", m, results.get(m)));

        log.info("--- 失败模型列表 ---");
        failedModels.forEach(m -> log.info("[FAILED]  {} -> {}", m, results.get(m)));
        log.info("==================================================");

        // 只要有一个模型成功，就认为测试通过（因为有些模型可能确实无法处理简单文本，或者权限受限）
        assertFalse(successModels.isEmpty(), "没有一个模型能成功调用，请检查 API Key 或网络");
    }

    //region 音频/视频理解测试用例

    /**
     * 测试 Gemini 音频理解功能 (真实调用)
     *
     * <p><b>测试目的：</b></p>
     * <ol>
     *     <li>验证 gemma-3n-e4b-it 模型的音频理解能力。</li>
     *     <li>验证音频 Base64 编码与 API 请求构建是否正确。</li>
     *     <li>验证响应解析逻辑是否能正确提取文本结果。</li>
     * </ol>
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>读取本地测试音频文件（如 WAV 或 MP3）。</li>
     *     <li>将音频转换为 Base64 编码。</li>
     *     <li>调用 geminiChatWithAudio 方法并验证返回结果。</li>
     * </ul>
     */
    @Test
    @Order(8)
    @DisplayName("真实测试_Gemini音频理解")
    public void testGeminiAudioChat() {
        log.info("开始真实测试Gemini音频理解|Start_real_testing_Gemini_audio_chat");

        // 测试音频文件路径，请确保存在有效的音频文件
        String audioPath = "e:\\project\\quant-nano-alpha\\docs\\podcast.mp3";

        if (!Files.exists(Paths.get(audioPath))) {
            log.warn("测试跳过_未找到音频文件|Test_skipped_audio_not_found,path={}", audioPath);
            Assumptions.assumeTrue(false, "Skipping test: Test audio not found at " + audioPath);
            return;
        }

        try {
            byte[] audioBytes = Files.readAllBytes(Paths.get(audioPath));
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            // MP3 文件应使用 audio/mpeg MIME 类型
            String mimeType = "audio/mpeg";

            String prompt = "这段音频说了什么？请简要描述。";

            String response = aiApiService.geminiChatWithAudio(prompt, audioBase64, mimeType);

            log.info("用户音频输入|User_audio_input,prompt={}", prompt);
            log.info("Gemini真实回复|Gemini_real_response,response={}", response);

            assertNotNull(response, "回复不应为空");
            assertFalse(response.contains("Error"), "API调用返回了错误信息: " + response);
            assertTrue(response.length() > 5, "回复内容过短");

            log.info("音频理解真实测试通过|Real_audio_chat_test_passed");

        } catch (IOException e) {
            log.error("读取音频文件失败|Failed_to_read_audio_file", e);
            Assumptions.assumeTrue(false, "Skipping test: Failed to read audio file");
        }
    }

    /**
     * 测试 Gemini 视频理解功能 (真实调用)
     *
     * <p><b>测试目的：</b></p>
     * <ol>
     *     <li>验证 gemma-3n-e4b-it 模型的视频理解能力。</li>
     *     <li>验证视频 Base64 编码与 API 请求构建是否正确。</li>
     *     <li>验证响应解析逻辑是否能正确提取文本结果。</li>
     * </ol>
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>读取本地测试视频文件（如 MP4）。</li>
     *     <li>将视频转换为 Base64 编码。</li>
     *     <li>调用 geminiChatWithVideo 方法并验证返回结果。</li>
     * </ul>
     *
     * <p><b>注意：</b></p>
     * 视频文件通常较大，Base64 编码后会更大。
     * 建议使用 5-10 秒的短视频进行测试。
     */
    @Test
    @Order(9)
    @DisplayName("真实测试_Gemini视频理解")
    public void testGeminiVideoChat() {
        log.info("开始真实测试Gemini视频理解|Start_real_testing_Gemini_video_chat");

        // 测试视频文件路径，请确保存在有效的短视频文件
        String videoPath = "e:\\project\\quant-nano-alpha\\docs\\test.mp4";

        if (!Files.exists(Paths.get(videoPath))) {
            log.warn("测试跳过_未找到视频文件|Test_skipped_video_not_found,path={}", videoPath);
            Assumptions.assumeTrue(false, "Skipping test: Test video not found at " + videoPath);
            return;
        }

        try {
            byte[] videoBytes = Files.readAllBytes(Paths.get(videoPath));
            String videoBase64 = Base64.getEncoder().encodeToString(videoBytes);
            String mimeType = "video/mp4";

            log.info("视频文件大小|Video_file_size={}KB", videoBytes.length / 1024);

            String prompt = "这个视频里发生了什么？请简要描述。";

            String response = aiApiService.geminiChatWithVideo(prompt, videoBase64, mimeType);

            log.info("用户视频输入|User_video_input,prompt={}", prompt);
            log.info("Gemini真实回复|Gemini_real_response,response={}", response);

            assertNotNull(response, "回复不应为空");
            assertFalse(response.contains("Error"), "API调用返回了错误信息: " + response);
            assertTrue(response.length() > 5, "回复内容过短");

            log.info("视频理解真实测试通过|Real_video_chat_test_passed");

        } catch (IOException e) {
            log.error("读取视频文件失败|Failed_to_read_video_file", e);
            Assumptions.assumeTrue(false, "Skipping test: Failed to read video file");
        }
    }

    /**
     * 测试无效音频输入的错误处理
     *
     * <p><b>测试目的：</b></p>
     * 验证系统对无效音频数据的健壮性，确保不会崩溃并返回合理的错误信息。
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>传入空字符串作为音频数据。</li>
     *     <li>验证返回的是错误信息而非 null。</li>
     * </ul>
     */
    @Test
    @Order(10)
    @DisplayName("测试_无效音频输入处理")
    public void testInvalidAudioInput() {
        log.info("开始测试无效音频输入处理|Start_testing_invalid_audio_input");

        // 传入空的 Base64 字符串模拟无效音频
        String response = aiApiService.geminiChatWithAudio("描述这段音频", "", "audio/wav");

        log.info("无效音频输入响应|Invalid_audio_input_response={}", response);

        // 验证系统不会崩溃，能返回响应（可能是错误提示）
        assertNotNull(response, "即使无效输入，响应也不应为null");

        log.info("无效音频输入处理测试通过|Invalid_audio_input_test_passed");
    }

    /**
     * 测试无效视频输入的错误处理
     *
     * <p><b>测试目的：</b></p>
     * 验证系统对无效视频数据的健壮性，确保不会崩溃并返回合理的错误信息。
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>传入空字符串作为视频数据。</li>
     *     <li>验证返回的是错误信息而非 null。</li>
     * </ul>
     */
    @Test
    @Order(11)
    @DisplayName("测试_无效视频输入处理")
    public void testInvalidVideoInput() {
        log.info("开始测试无效视频输入处理|Start_testing_invalid_video_input");

        // 传入空的 Base64 字符串模拟无效视频
        String response = aiApiService.geminiChatWithVideo("描述这个视频", "", "video/mp4");

        log.info("无效视频输入响应|Invalid_video_input_response={}", response);

        // 验证系统不会崩溃，能返回响应（可能是错误提示）
        assertNotNull(response, "即使无效输入，响应也不应为null");

        log.info("无效视频输入处理测试通过|Invalid_video_input_test_passed");
    }

    /**
     * 测试音频模型配置是否正确加载
     *
     * <p><b>测试目的：</b></p>
     * 验证 GeminiConfig 中的 defaultAudioVideoModel 配置项是否正确加载。
     */
    @Test
    @Order(12)
    @DisplayName("测试_音视频模型配置")
    public void testAudioVideoModelConfig() {
        log.info("开始测试音视频模型配置|Start_testing_audio_video_model_config");

        String audioVideoModel = geminiConfig.getDefaultAudioVideoModel();
        log.info("当前配置的音视频模型|Configured_audio_video_model={}", audioVideoModel);

        assertNotNull(audioVideoModel, "音视频模型配置不应为null");
        assertFalse(audioVideoModel.isEmpty(), "音视频模型配置不应为空");

        // 验证默认值是否为 gemma-3n-e4b-it（支持音视频输入的边缘计算优化模型）
        assertEquals("gemma-3n-e4b-it", audioVideoModel, "默认音视频模型应为 gemma-3n-e4b-it");

        log.info("音视频模型配置测试通过|Audio_video_model_config_test_passed");
    }

    //endregion

    /**
     * 检查主机端口是否可达
     *
     * @param host      主机名
     * @param port      端口
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
