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

    /**
     * 发送带音频的提示词给 Gemini 进行音频理解
     *
     * 实现逻辑：
     * 1. 将音频数据编码为Base64格式。
     * 2. 构建符合Gemini多模态协议的请求体。
     * 3. 调用gemma-3n-e4b-it模型进行音频分析。
     * 4. 返回模型对音频内容的文本描述或问答结果。
     *
     * @param prompt      用户提示词（如"这段音频说了什么？"）
     * @param audioBase64 音频Base64字符串
     * @param mimeType    音频类型，如 audio/wav, audio/mp3, audio/mpeg
     * @return 模型回复（文本格式）
     */
    String geminiChatWithAudio(String prompt, String audioBase64, String mimeType);

    /**
     * 发送带视频的提示词给 Gemini 进行视频理解
     *
     * 实现逻辑：
     * 1. 将视频数据编码为Base64格式。
     * 2. 构建符合Gemini多模态协议的请求体。
     * 3. 调用gemma-3n-e4b-it模型进行视频分析。
     * 4. 返回模型对视频内容的文本描述或问答结果。
     *
     * @param prompt      用户提示词（如"这个视频里发生了什么？"）
     * @param videoBase64 视频Base64字符串
     * @param mimeType    视频类型，如 video/mp4, video/webm
     * @return 模型回复（文本格式）
     */
    String geminiChatWithVideo(String prompt, String videoBase64, String mimeType);
}
