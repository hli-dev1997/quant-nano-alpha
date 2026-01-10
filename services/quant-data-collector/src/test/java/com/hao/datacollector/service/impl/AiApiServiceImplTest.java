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

    //region Token限制与并发测试

    /**
     * Token 限制测试 - 测试 Gemini API 最大支持的 token 数量
     *
     * <p><b>测试目的：</b></p>
     * <ol>
     *     <li>探测 API 支持的最大输入 token 数量。</li>
     *     <li>记录不同 token 长度下的响应时间变化。</li>
     *     <li>识别 token 限制触发时的错误类型。</li>
     * </ol>
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>逐步增加输入文本长度（按 token 估算）。</li>
     *     <li>记录每次调用的成功/失败状态和响应时间。</li>
     *     <li>找到触发限制的临界点。</li>
     * </ul>
     *
     * <p><b>Token 估算规则：</b></p>
     * 中文约 1-2 字符 = 1 token，英文约 4 字符 = 1 token。
     * 为简化测试，使用重复文本块逐步扩大输入。
     */
    @Test
    @Order(14)
    @DisplayName("极限测试_Gemini最大Token支持")
    public void testGeminiMaxTokenLimit() {
        log.info("开始测试Gemini最大Token支持|Start_testing_Gemini_max_token_limit");

        // 基础文本块（约 100 个中文字符 ≈ 50-100 tokens）
        String baseBlock = "这是一段用于测试Token限制的中文文本。我们需要逐步增加文本长度来探测API的最大输入限制。"
                + "量化交易系统需要分析大量的市场数据和新闻资讯，因此了解API的token限制非常重要。";

        // 测试不同的 token 量级
        int[] tokenLevels = {100, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000};

        List<Map<String, Object>> results = new ArrayList<>();
        int lastSuccessTokens = 0;
        int firstFailTokens = -1;

        log.info("================================================");
        log.info("Token 限制测试开始 | Token Limit Test Start");
        log.info("================================================");

        for (int targetTokens : tokenLevels) {
            // 构建指定长度的文本（按中文估算，约 2 字符 = 1 token）
            int targetChars = targetTokens * 2;
            StringBuilder sb = new StringBuilder();
            while (sb.length() < targetChars) {
                sb.append(baseBlock);
            }
            String longText = sb.substring(0, Math.min(sb.length(), targetChars));

            String prompt = "请阅读以下文本并给出一句话总结：\n\n" + longText + "\n\n请用一句话总结上述内容。";
            int actualChars = prompt.length();
            int estimatedTokens = actualChars / 2; // 粗略估算

            log.info("测试目标: {} tokens (实际约 {} 字符, 估算 {} tokens)",
                    targetTokens, actualChars, estimatedTokens);

            long startTime = System.currentTimeMillis();
            Map<String, Object> result = new HashMap<>();
            result.put("targetTokens", targetTokens);
            result.put("actualChars", actualChars);
            result.put("estimatedTokens", estimatedTokens);

            try {
                String response = aiApiService.geminiChat(prompt);
                long elapsed = System.currentTimeMillis() - startTime;

                result.put("elapsed", elapsed);

                if (response != null && !response.contains("Error") && !response.contains("too long")
                        && !response.contains("exceeds") && !response.contains("limit")) {
                    result.put("status", "SUCCESS");
                    result.put("responseLength", response.length());
                    result.put("preview", response.substring(0, Math.min(80, response.length())).replace("\n", " "));
                    lastSuccessTokens = estimatedTokens;
                    log.info("  -> 成功 | {}ms | 回复长度: {}", elapsed, response.length());
                } else {
                    result.put("status", "FAILED");
                    result.put("error", response);
                    if (firstFailTokens < 0) firstFailTokens = estimatedTokens;
                    log.warn("  -> 失败 | {}ms | 错误: {}", elapsed,
                            response != null ? response.substring(0, Math.min(100, response.length())) : "null");
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                result.put("elapsed", elapsed);
                result.put("status", "EXCEPTION");
                result.put("error", e.getMessage());
                if (firstFailTokens < 0) firstFailTokens = estimatedTokens;
                log.error("  -> 异常 | {}ms | {}", elapsed, e.getMessage());
            }

            results.add(result);

            // 如果连续失败两次则停止继续测试
            long failCount = results.stream()
                    .filter(r -> !"SUCCESS".equals(r.get("status")))
                    .count();
            if (failCount >= 2 && results.size() > 3) {
                log.info("连续失败，停止测试");
                break;
            }

            // 大请求间隔久一点，避免限流
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 输出测试报告
        log.info("================================================");
        log.info("Token 限制测试报告 | Token Limit Test Report");
        log.info("================================================");
        log.info("测试轮数: {}", results.size());
        log.info("最大成功 Token 数(估算): {}", lastSuccessTokens);
        if (firstFailTokens > 0) {
            log.info("首次失败 Token 数(估算): {}", firstFailTokens);
            log.info("推测 Token 上限区间: {} ~ {}", lastSuccessTokens, firstFailTokens);
        }
        log.info("------------------------------------------------");
        for (Map<String, Object> r : results) {
            log.info("[{}] {} tokens, {} chars, {}ms, {}",
                    r.get("status"), r.get("targetTokens"), r.get("actualChars"),
                    r.get("elapsed"), r.containsKey("preview") ? r.get("preview") : r.get("error"));
        }
        log.info("================================================");

        // 至少要有一次成功
        assertTrue(lastSuccessTokens > 0, "至少应该有一次成功调用");
    }

    /**
     * 并发调用测试 - 测试 Gemini API 最大并发数
     *
     * <p><b>测试目的：</b></p>
     * <ol>
     *     <li>探测 API 支持的最大并发请求数。</li>
     *     <li>记录不同并发量下的成功率和响应时间。</li>
     *     <li>识别并发限制触发时的错误类型（如 429 Too Many Requests）。</li>
     * </ol>
     *
     * <p><b>测试思路：</b></p>
     * <ul>
     *     <li>使用多线程同时发起多个请求。</li>
     *     <li>逐步增加并发数直到触发限制。</li>
     *     <li>统计成功率、限流率、平均响应时间。</li>
     * </ul>
     */
    @Test
    @Order(15)
    @DisplayName("极限测试_Gemini最大并发调用")
    public void testGeminiMaxConcurrency() throws InterruptedException {
        log.info("开始测试Gemini最大并发调用|Start_testing_Gemini_max_concurrency");

        // 测试不同的并发级别
        int[] concurrencyLevels = {1, 2, 5, 10, 15, 20, 30, 50};

        List<Map<String, Object>> allResults = new ArrayList<>();

        log.info("================================================");
        log.info("并发测试开始 | Concurrency Test Start");
        log.info("================================================");

        for (int concurrency : concurrencyLevels) {
            log.info("\n--- 测试并发数: {} ---", concurrency);

            // 使用 CountDownLatch 同步所有线程同时开始
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch endLatch = new java.util.concurrent.CountDownLatch(concurrency);

            // 存储每个线程的结果
            List<Map<String, Object>> threadResults = Collections.synchronizedList(new ArrayList<>());

            // 创建并发线程
            for (int i = 0; i < concurrency; i++) {
                final int threadId = i;
                new Thread(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("threadId", threadId);

                    try {
                        // 等待所有线程就绪
                        startLatch.await();

                        long start = System.currentTimeMillis();
                        String prompt = "你好，这是并发测试请求 #" + threadId + "，请简短回复OK。";

                        try {
                            String response = aiApiService.geminiChat(prompt);
                            long elapsed = System.currentTimeMillis() - start;

                            result.put("elapsed", elapsed);

                            if (response != null && !response.contains("Error")
                                    && !response.toLowerCase().contains("rate")
                                    && !response.contains("429")) {
                                result.put("status", "SUCCESS");
                                result.put("responseLength", response.length());
                            } else if (response != null && (response.toLowerCase().contains("rate")
                                    || response.contains("429"))) {
                                result.put("status", "RATE_LIMITED");
                                result.put("error", response.substring(0, Math.min(100, response.length())));
                            } else {
                                result.put("status", "FAILED");
                                result.put("error", response);
                            }
                        } catch (Exception e) {
                            long elapsed = System.currentTimeMillis() - start;
                            result.put("elapsed", elapsed);

                            if (e.getMessage() != null && e.getMessage().contains("429")) {
                                result.put("status", "RATE_LIMITED");
                            } else {
                                result.put("status", "EXCEPTION");
                            }
                            result.put("error", e.getMessage());
                        }
                    } catch (InterruptedException e) {
                        result.put("status", "INTERRUPTED");
                        Thread.currentThread().interrupt();
                    } finally {
                        threadResults.add(result);
                        endLatch.countDown();
                    }
                }).start();
            }

            // 记录开始时间并释放所有线程
            long batchStart = System.currentTimeMillis();
            startLatch.countDown();

            // 等待所有线程完成（最多30秒）
            boolean completed = endLatch.await(60, java.util.concurrent.TimeUnit.SECONDS);
            long batchElapsed = System.currentTimeMillis() - batchStart;

            if (!completed) {
                log.warn("部分线程超时未完成");
            }

            // 统计结果
            long successCount = threadResults.stream()
                    .filter(r -> "SUCCESS".equals(r.get("status")))
                    .count();
            long rateLimitCount = threadResults.stream()
                    .filter(r -> "RATE_LIMITED".equals(r.get("status")))
                    .count();
            long failCount = threadResults.stream()
                    .filter(r -> "FAILED".equals(r.get("status")) || "EXCEPTION".equals(r.get("status")))
                    .count();

            double avgElapsed = threadResults.stream()
                    .filter(r -> r.get("elapsed") != null)
                    .mapToLong(r -> (Long) r.get("elapsed"))
                    .average()
                    .orElse(0);

            Map<String, Object> batchResult = new HashMap<>();
            batchResult.put("concurrency", concurrency);
            batchResult.put("totalTime", batchElapsed);
            batchResult.put("success", successCount);
            batchResult.put("rateLimited", rateLimitCount);
            batchResult.put("failed", failCount);
            batchResult.put("avgElapsed", avgElapsed);
            batchResult.put("successRate", (successCount * 100.0 / concurrency));
            allResults.add(batchResult);

            log.info("并发数: {} | 总耗时: {}ms | 成功: {} | 限流: {} | 失败: {} | 成功率: {}% | 平均响应: {}ms",
                    concurrency, batchElapsed, successCount, rateLimitCount, failCount,
                    String.format("%.1f", successCount * 100.0 / concurrency),
                    String.format("%.0f", avgElapsed));

            // 如果限流率超过 50%，停止继续增加并发
            if (rateLimitCount > concurrency * 0.5) {
                log.info("限流率超过50%，停止增加并发");
                break;
            }

            // 批次间等待，让 API 恢复
            Thread.sleep(5000);
        }

        // 输出综合报告
        log.info("\n================================================");
        log.info("并发测试综合报告 | Concurrency Test Report");
        log.info("================================================");
        log.info("| 并发数 | 成功率 | 成功 | 限流 | 失败 | 平均响应 | 总耗时 |");
        log.info("|--------|--------|------|------|------|----------|--------|");
        for (Map<String, Object> r : allResults) {
            log.info("| {:^6} | {:>5.1f}% | {:>4} | {:>4} | {:>4} | {:>6.0f}ms | {:>5}ms |",
                    r.get("concurrency"), r.get("successRate"),
                    r.get("success"), r.get("rateLimited"), r.get("failed"),
                    r.get("avgElapsed"), r.get("totalTime"));
        }
        log.info("================================================");

        // 找出最大成功并发数
        int maxSuccessConcurrency = allResults.stream()
                .filter(r -> ((Long) r.get("success")).intValue() == (Integer) r.get("concurrency"))
                .mapToInt(r -> (Integer) r.get("concurrency"))
                .max()
                .orElse(0);

        log.info("最大无限流并发数: {}", maxSuccessConcurrency);
        log.info("================================================");

        // 至少并发1要成功
        assertTrue(allResults.stream().anyMatch(r -> (Long) r.get("success") > 0L),
                "至少应该有成功的并发请求");
    }

    //endregion Token限制与并发测试

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
