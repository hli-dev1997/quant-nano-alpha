package com.hao.datacollector.web.controller;

import com.hao.datacollector.service.AiApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
