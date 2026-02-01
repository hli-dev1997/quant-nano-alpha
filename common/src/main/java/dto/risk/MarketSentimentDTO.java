package dto.risk;

import java.io.Serial;
import java.io.Serializable;

/**
 * 市场情绪数据传输对象 (Market Sentiment DTO)
 * <p>
 * 类职责：
 * 跨模块传递市场情绪评分数据，由风控模块推送到 Redis，信号中心读取使用。
 * <p>
 * 使用场景：
 * 1. quant-risk-control 计算市场情绪后推送到 Redis
 * 2. quant-signal-center 从 Redis 读取用于风控判断
 * 3. 前端仪表盘展示市场情绪状态
 * <p>
 * 过期机制：
 * 1. timestamp：数据生成时间戳
 * 2. expireTimestamp：数据过期时间戳（默认 timestamp + 3秒）
 * 3. 信号中心判断：当前时间 > expireTimestamp 时进入降级模式
 *
 * @author hli
 * @date 2026-02-01
 */
public class MarketSentimentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认数据有效期：3 秒（毫秒）
     */
    public static final long DEFAULT_TTL_MS = 3000L;

    /**
     * 百分制得分（0-100）
     * <p>
     * 75-100：强势区，50-74：震荡偏强，25-49：震荡偏弱，0-24：弱势区
     */
    private Integer score;

    /**
     * 原始综合涨跌幅（整数基点）
     * <p>
     * 例如：150 表示综合涨跌 +1.5%，-98 表示综合涨跌 -0.98%
     */
    private Integer rawChange;

    /**
     * 市场区间名称
     * <p>
     * 可选值：强势区、震荡偏强区、震荡偏弱区、弱势区
     */
    private String zoneName;

    /**
     * 操作建议
     * <p>
     * 例如：积极进攻、持仓或优化、防御减仓、严格风控
     */
    private String suggestion;

    /**
     * 数据生成时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 数据过期时间戳（毫秒）
     * <p>
     * 默认为 timestamp + 3秒。
     * 信号中心判断：System.currentTimeMillis() > expireTimestamp 时进入降级模式。
     */
    private Long expireTimestamp;

    /**
     * 格式化的涨跌幅显示
     * <p>
     * 例如："+1.50%" 或 "-0.98%"
     */
    private String formattedChange;

    /**
     * 默认构造函数
     */
    public MarketSentimentDTO() {
    }

    /**
     * 判断数据是否已过期
     *
     * @return true-已过期需要降级
     */
    public boolean isExpired() {
        if (expireTimestamp == null) {
            return true;
        }
        return System.currentTimeMillis() > expireTimestamp;
    }

    // ==================== Getter & Setter ====================

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getRawChange() {
        return rawChange;
    }

    public void setRawChange(Integer rawChange) {
        this.rawChange = rawChange;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getExpireTimestamp() {
        return expireTimestamp;
    }

    public void setExpireTimestamp(Long expireTimestamp) {
        this.expireTimestamp = expireTimestamp;
    }

    public String getFormattedChange() {
        return formattedChange;
    }

    public void setFormattedChange(String formattedChange) {
        this.formattedChange = formattedChange;
    }

    @Override
    public String toString() {
        return "MarketSentimentDTO{" +
                "score=" + score +
                ", rawChange=" + rawChange +
                ", zoneName='" + zoneName + '\'' +
                ", suggestion='" + suggestion + '\'' +
                ", timestamp=" + timestamp +
                ", expireTimestamp=" + expireTimestamp +
                ", formattedChange='" + formattedChange + '\'' +
                '}';
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MarketSentimentDTO dto = new MarketSentimentDTO();

        public Builder score(Integer score) {
            dto.score = score;
            return this;
        }

        public Builder rawChange(Integer rawChange) {
            dto.rawChange = rawChange;
            return this;
        }

        public Builder zoneName(String zoneName) {
            dto.zoneName = zoneName;
            return this;
        }

        public Builder suggestion(String suggestion) {
            dto.suggestion = suggestion;
            return this;
        }

        public Builder timestamp(Long timestamp) {
            dto.timestamp = timestamp;
            return this;
        }

        public Builder expireTimestamp(Long expireTimestamp) {
            dto.expireTimestamp = expireTimestamp;
            return this;
        }

        public Builder formattedChange(String formattedChange) {
            dto.formattedChange = formattedChange;
            return this;
        }

        public MarketSentimentDTO build() {
            // 如果未设置 expireTimestamp，默认为 timestamp + 3秒
            if (dto.expireTimestamp == null && dto.timestamp != null) {
                dto.expireTimestamp = dto.timestamp + DEFAULT_TTL_MS;
            }
            return dto;
        }
    }
}
