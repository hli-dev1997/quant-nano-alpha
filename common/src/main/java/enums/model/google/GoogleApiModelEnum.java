package enums.model.google;

import lombok.Getter;

/**
 * Google API 模型枚举
 * <p>
 * 包含 Google 提供的各类 AI 模型，如 Gemma 系列、Gemini 系列等。
 * </p>
 *
 * @author hli
 * @date 2026-01-02
 */
@Getter
public enum GoogleApiModelEnum {

    //region Gemma 3 系列模型 (Open Weights)
    /**
     * <b>Gemma 3 - 文字问答模型 (Text QA)</b>
     * <ul>
     *     <li><b>模型名称：</b> gemma-3-1b-it</li>
     *     <li><b>能力：</b> 专门用于纯文本任务。</li>
     *     <li><b>特点：</b> 速度极快，适合简单的对话、文本摘要或在边缘设备上运行。通常不具备多模态理解能力。</li>
     * </ul>
     */
    GEMMA_3_TEXT_QA_1B("gemma-3-1b-it", GoogleModelSeriesEnum.GEMMA, ModelCapabilityEnum.TEXT_ONLY, "速度极快，适合简单对话和摘要"),

    /**
     * <b>Gemma 3 - 图片问答模型 (Image QA / Visual QA) - 快速版</b>
     * <ul>
     *     <li><b>模型名称：</b> gemma-3-4b-it</li>
     *     <li><b>能力：</b> 原生多模态模型，支持读取和分析图片。</li>
     *     <li><b>特点：</b> 速度较快，支持 OCR、物体识别等视觉任务。</li>
     * </ul>
     */
    GEMMA_3_IMAGE_QA_4B("gemma-3-4b-it", GoogleModelSeriesEnum.GEMMA, ModelCapabilityEnum.IMAGE_UNDERSTANDING, "速度快，支持基础视觉任务"),

    /**
     * <b>Gemma 3 - 图片问答模型 (Image QA / Visual QA) - 平衡版</b>
     * <ul>
     *     <li><b>模型名称：</b> gemma-3-12b-it</li>
     *     <li><b>能力：</b> 原生多模态模型，支持读取和分析图片。</li>
     *     <li><b>特点：</b> 性能与速度的平衡点，适合大多数视觉推理场景。</li>
     * </ul>
     */
    GEMMA_3_IMAGE_QA_12B("gemma-3-12b-it", GoogleModelSeriesEnum.GEMMA, ModelCapabilityEnum.IMAGE_UNDERSTANDING, "性能均衡，推荐用于通用视觉任务"),

    /**
     * <b>Gemma 3 - 图片问答模型 (Image QA / Visual QA) - 高精度版</b>
     * <ul>
     *     <li><b>模型名称：</b> gemma-3-27b-it</li>
     *     <li><b>能力：</b> 原生多模态模型，支持读取和分析图片。</li>
     *     <li><b>特点：</b> 推理能力最强，适合处理复杂的视觉逻辑和长上下文。</li>
     * </ul>
     */
    GEMMA_3_IMAGE_QA_27B("gemma-3-27b-it", GoogleModelSeriesEnum.GEMMA, ModelCapabilityEnum.IMAGE_UNDERSTANDING, "高精度，适合复杂逻辑推理"),

    /**
     * <b>Gemma 3 - 视频/音频/全模态问答模型 (Video & Audio QA)</b>
     * <ul>
     *     <li><b>模型名称：</b> gemma-3n-e4b-it</li>
     *     <li><b>能力：</b> "N" (Native/Nano) 版本，专为移动端优化。</li>
     *     <li><b>特点：</b> 全模态支持，通常支持音频 (Audio) 和 视频 (Video) 输入，适合分析短视频或音频流。</li>
     * </ul>
     */
    GEMMA_3_FULL_MODAL_NANO("gemma-3n-e4b-it", GoogleModelSeriesEnum.GEMMA, ModelCapabilityEnum.FULL_MULTIMODAL, "支持视频和音频输入，边缘计算优化"),
    //endregion
    ;

    /**
     * 模型实际调用名称
     */
    private final String modelName;

    /**
     * 所属系列
     */
    private final GoogleModelSeriesEnum series;

    /**
     * 核心能力
     */
    private final ModelCapabilityEnum capability;

    /**
     * 简要描述
     */
    private final String description;

    GoogleApiModelEnum(String modelName, GoogleModelSeriesEnum series, ModelCapabilityEnum capability, String description) {
        this.modelName = modelName;
        this.series = series;
        this.capability = capability;
        this.description = description;
    }

    /**
     * 根据模型名称查找枚举
     *
     * @param modelName 模型名称
     * @return 对应的枚举，如果未找到则返回 null
     */
    public static GoogleApiModelEnum fromModelName(String modelName) {
        for (GoogleApiModelEnum type : values()) {
            if (type.getModelName().equalsIgnoreCase(modelName)) {
                return type;
            }
        }
        return null;
    }
}
