package com.hao.datacollector.web.controller;

import com.hao.datacollector.service.AiApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

/**
 * 模型接口控制器
 *
 * 职责：对外提供模型对话入口，负责参数接收与请求转发。
 *
 * 设计目的：
 * 1. 将Web层与模型服务调用解耦，集中控制入参与日志。
 * 2. 统一接口路径与响应格式，便于后续鉴权与限流扩展。
 *
 * 为什么需要该类：
 * - 需要专门的控制器承接外部请求并隔离业务服务。
 *
 * 核心实现思路：
 * - 使用Spring MVC接收请求并委派给AiApiService。
 * - 记录关键链路日志，便于追踪请求结果。
 *
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2025-12-01 14:18:41
 * @description: 模型API
 */
@Slf4j
@Tag(name = "模型API")
@RequestMapping("ai_api")
@RestController
public class AiApiController {

    @Autowired
    private AiApiService aiApiService;

    /**
     * 调用模型对话接口
     *
     * 实现逻辑：
     * 1. 接收并校验用户输入参数。
     * 2. 调用AiApiService获取模型回复。
     * 3. 记录关键日志并返回结果。
     *
     * @param input 用户输入
     * @return 模型回复内容
     */
    @GetMapping("/openai_chat")
    public String chat(@RequestParam String input) {
        // 实现思路：
        // 1. 记录请求入口日志。
        // 2. 调用服务获取模型回复。
        // 3. 记录结果日志并返回响应。
        log.info("开始处理模型对话请求|Start_ai_chat_request,input={}", input);
        // 调用模型服务获取回复内容
        String response = aiApiService.openAiChat(input);
        log.info("模型对话请求完成|Ai_chat_request_completed,response={}", response);
        return response;
    }

    /**
     * 调用Gemini模型对话接口
     *
     * 实现逻辑：
     * 1. 接收并校验用户输入参数。
     * 2. 调用AiApiService获取Gemini模型回复。
     * 3. 记录关键日志并返回结果。
     *
     * @param input 用户输入
     * @return 模型回复内容
     */
    @GetMapping("/gemini_chat")
    public String geminiChat(@RequestParam String input) {
        // 实现思路：
        // 1. 记录请求入口日志。
        // 2. 调用服务获取Gemini模型回复。
        // 3. 记录结果日志并返回响应。
        log.info("开始处理Gemini模型对话请求|Start_gemini_chat_request,input={}", input);
        String response = aiApiService.geminiChat(input);
        log.info("Gemini模型对话请求完成|Gemini_chat_request_completed,response={}", response);
        return response;
    }

    /**
     * 获取当前API Key可用的Gemini模型列表
     *
     * 实现逻辑：
     * 1. 调用AiApiService获取模型列表。
     * 2. 返回原始JSON字符串。
     *
     * @return 模型列表JSON
     */
    @GetMapping("/gemini_models")
    public String listGeminiModels() {
        log.info("开始获取Gemini模型列表|Start_listing_Gemini_models");
        String models = aiApiService.listGeminiModels();
        log.info("Gemini模型列表获取完成|Gemini_models_list_completed");
        return models;
    }

    /**
     * 调用Gemini音频理解接口
     *
     * 实现逻辑：
     * 1. 接收音频文件和用户提示词。
     * 2. 将音频转换为Base64编码。
     * 3. 调用AiApiService获取音频分析结果。
     * 4. 记录关键日志并返回结果。
     *
     * @param audioFile 音频文件（支持 wav, mp3, mpeg 等格式）
     * @param prompt    用户提示词（如"这段音频说了什么？"）
     * @return 模型回复（文本格式）
     */
    // TODO: 当前配置的 gemma-3n-e4b-it 模型虽然架构上支持音频输入，但 Google REST API (v1beta) 尚未开放此模态。
    //       如需启用音频理解功能，需将 ai.gemini.audio-video-model 改为 Gemini 系列（如 gemini-2.0-flash-exp）。
    @Operation(summary = "Gemini音频理解", description = "上传音频文件，AI分析音频内容并回答问题")
    @PostMapping("/gemini_audio_chat")
    public String geminiAudioChat(
            @RequestParam("file") MultipartFile audioFile,
            @RequestParam(value = "prompt", defaultValue = "请描述这段音频的内容") String prompt) {
        // 实现思路：
        // 1. 验证文件是否为空。
        // 2. 将文件内容转换为Base64编码。
        // 3. 获取文件MIME类型。
        // 4. 调用服务进行音频理解。
        try {
            if (audioFile.isEmpty()) {
                log.warn("收到空音频文件|Received_empty_audio_file");
                return "Error: Audio file is empty";
            }

            String audioBase64 = Base64.getEncoder().encodeToString(audioFile.getBytes());
            String mimeType = audioFile.getContentType() != null ? audioFile.getContentType() : "audio/mpeg";

            log.info("开始处理Gemini音频理解请求|Start_gemini_audio_chat_request,prompt={},mimeType={},fileSize={}",
                    prompt, mimeType, audioFile.getSize());

            String response = aiApiService.geminiChatWithAudio(prompt, audioBase64, mimeType);

            log.info("Gemini音频理解请求完成|Gemini_audio_chat_request_completed,responseLength={}",
                    response != null ? response.length() : 0);
            return response;

        } catch (Exception e) {
            log.error("Gemini音频理解请求失败|Gemini_audio_chat_request_failed", e);
            return "Error while processing audio: " + e.getMessage();
        }
    }

    /**
     * 调用Gemini视频理解接口
     *
     * 实现逻辑：
     * 1. 接收视频文件和用户提示词。
     * 2. 将视频转换为Base64编码。
     * 3. 调用AiApiService获取视频分析结果。
     * 4. 记录关键日志并返回结果。
     *
     * @param videoFile 视频文件（支持 mp4, webm 等格式）
     * @param prompt    用户提示词（如"这个视频里发生了什么？"）
     * @return 模型回复（文本格式）
     */
    // TODO: 当前配置的 gemma-3n-e4b-it 模型虽然架构上支持视频输入，但 Google REST API (v1beta) 尚未开放此模态。
    //       如需启用视频理解功能，需将 ai.gemini.audio-video-model 改为 Gemini 系列（如 gemini-2.0-flash-exp）。
    @Operation(summary = "Gemini视频理解", description = "上传视频文件，AI分析视频内容并回答问题")
    @PostMapping("/gemini_video_chat")
    public String geminiVideoChat(
            @RequestParam("file") MultipartFile videoFile,
            @RequestParam(value = "prompt", defaultValue = "请描述这个视频的内容") String prompt) {
        // 实现思路：
        // 1. 验证文件是否为空。
        // 2. 将文件内容转换为Base64编码。
        // 3. 获取文件MIME类型。
        // 4. 调用服务进行视频理解。
        try {
            if (videoFile.isEmpty()) {
                log.warn("收到空视频文件|Received_empty_video_file");
                return "Error: Video file is empty";
            }

            String videoBase64 = Base64.getEncoder().encodeToString(videoFile.getBytes());
            String mimeType = videoFile.getContentType() != null ? videoFile.getContentType() : "video/mp4";

            log.info("开始处理Gemini视频理解请求|Start_gemini_video_chat_request,prompt={},mimeType={},fileSize={}",
                    prompt, mimeType, videoFile.getSize());

            String response = aiApiService.geminiChatWithVideo(prompt, videoBase64, mimeType);

            log.info("Gemini视频理解请求完成|Gemini_video_chat_request_completed,responseLength={}",
                    response != null ? response.length() : 0);
            return response;

        } catch (Exception e) {
            log.error("Gemini视频理解请求失败|Gemini_video_chat_request_failed", e);
            return "Error while processing video: " + e.getMessage();
        }
    }
}
