package enums.model.google;

import lombok.Getter;

/**
 * 模型能力分类枚举
 *
 * @author hli
 * @date 2026-01-02
 */
@Getter
public enum ModelCapabilityEnum {
    /**
     * 纯文本对话
     */
    TEXT_ONLY("Text Only", "仅支持文本输入和输出"),

    /**
     * 图片理解 (多模态)
     */
    IMAGE_UNDERSTANDING("Image Understanding", "支持图片输入和文本输出"),

    /**
     * 视频/音频理解 (全模态)
     */
    FULL_MULTIMODAL("Full Multimodal", "支持视频、音频、图片和文本输入");

    private final String capabilityName;
    private final String description;

    ModelCapabilityEnum(String capabilityName, String description) {
        this.capabilityName = capabilityName;
        this.description = description;
    }
}
