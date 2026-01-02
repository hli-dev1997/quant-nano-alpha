package com.hao.datacollector.service;

public interface AiApiService {

    /**
     * 发送提示词给 OpenAI 获取回复
     *
     * @param input 用户输入
     * @return 模型回复
     */
    String openAiChat(String input);

    /**
     * 发送提示词给 Gemini 获取回复
     *
     * @param input 用户输入
     * @return 模型回复
     */
    String geminiChat(String input);

    /**
     * 发送带图片的提示词给 Gemini
     *
     * @param prompt      用户提示词
     * @param imageBase64 图片Base64字符串
     * @param mimeType    图片类型，如 image/jpeg
     * @return 模型回复
     */
    String geminiChatWithImage(String prompt, String imageBase64, String mimeType);

    /**
     * 获取当前 API Key 可用的 Gemini 模型列表
     *
     * @return 模型列表的 JSON 字符串
     */
    String listGeminiModels();
}
