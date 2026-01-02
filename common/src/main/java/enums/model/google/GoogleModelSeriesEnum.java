package enums.model.google;

import lombok.Getter;

/**
 * Google 模型系列枚举
 *
 * @author hli
 * @date 2026-01-02
 */
@Getter
public enum GoogleModelSeriesEnum {
    /**
     * Gemma 系列 (Open Weights)
     * <p>Google 的开放权重模型，适合本地部署或特定任务微调。</p>
     */
    GEMMA("Gemma", "开放权重模型"),

    /**
     * Gemini 系列 (API Service)
     * <p>Google 最强大的多模态模型服务。</p>
     */
    GEMINI("Gemini", "旗舰多模态服务"),
    
    /**
     * PaLM 系列 (Legacy)
     * <p>上一代大语言模型。</p>
     */
    PALM("PaLM", "上一代模型");

    private final String seriesName;
    private final String description;

    GoogleModelSeriesEnum(String seriesName, String description) {
        this.seriesName = seriesName;
        this.description = description;
    }
}
