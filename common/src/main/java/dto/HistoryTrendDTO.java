package dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史分时数据传输对象 (History Trend DTO)
 * <p>
 * 类职责：
 * 封装股票分时行情数据，用于 Kafka 消息传递和策略计算。
 * <p>
 * 使用场景：
 * 1. 数据采集端从行情接口获取分时数据后封装为 DTO 发送到 Kafka
 * 2. 策略引擎消费 Kafka 消息，解析为 DTO 进行策略判断
 * 3. 风控模块实时计算市场情绪时使用
 *
 * @author hli
 * @date 2026-01-21
 */
@Data
public class HistoryTrendDTO {

    /**
     * 股票代码
     * 格式如：000001.SZ、600519.SH
     */
    private String windCode;

    /**
     * 交易日期时间
     * 精确到秒的行情时间戳
     */
    private LocalDateTime tradeDate;

    /**
     * 最新价
     * 当前股票的实时成交价格
     */
    private Double latestPrice;

    /**
     * 总成交量（手）
     * 从开盘到当前的累计成交量
     */
    private Double totalVolume;

    /**
     * 均价
     * 当日成交均价 = 成交额 / 成交量
     */
    private Double averagePrice;
}