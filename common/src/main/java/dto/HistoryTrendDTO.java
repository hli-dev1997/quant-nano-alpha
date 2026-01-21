package dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史分时数据传输对象
 */
@Data
public class HistoryTrendDTO {

    /** 股票代码 */
    private String windCode;

    /** 交易日期 */
    private LocalDateTime tradeDate;

    /** 最新价 */
    private Double latestPrice;

    /** 总成交量(手) */
    private Double totalVolume;

    /** 均价 */
    private Double averagePrice;
}