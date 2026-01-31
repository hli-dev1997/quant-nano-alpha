package enums.market;

import lombok.Getter;

/**
 * @description: 指数监控指标枚举
 */
@Getter
public enum IndexMonitorIndicatorEnum {
    
    // 核心价格指标
    CURRENT_PRICE(3, "最新价", "当前时刻的指数点位", 1),
    PREV_CLOSE_PRICE(83, "前收盘价", "前一交易日的收盘指数点位", 1),
    OPEN_PRICE(4, "开盘价", "当日开盘指数点位", 1),
    HIGH_PRICE(6, "最高价", "当日最高指数点位", 1),
    LOW_PRICE(7, "最低价", "当日最低指数点位", 1),
    
    // 核心涨跌指标
    CHANGE(80, "涨跌", "当前价与前收盘价的差值", 1),
    CHANGE_PERCENT(81, "涨跌幅", "当前价相对于前收盘价的涨跌百分比", 1),
    DAILY_CHANGE_PERCENT(81, "日内涨跌幅", "用于风控计算的日内涨跌幅", 10), // 最高优先级
    
    // 成交量指标
    VOLUME(8, "成交量", "当日累计成交量", 2),
    TURNOVER(59, "成交额", "当日累计成交金额", 2),
    
    // 市场广度指标
    ADVANCE_DECLINE_RATIO(217, "涨跌家数比", "上涨家数与下跌家数的比例", 3),
    ADVANCE_COUNT(217, "上涨家数", "市场中上涨的股票数量", 3),
    DECLINE_COUNT(218, "下跌家数", "市场中下跌的股票数量", 3),
    LIMIT_UP_COUNT(483, "涨停家数", "涨停板股票数量", 3),
    LIMIT_DOWN_COUNT(484, "跌停家数", "跌停板股票数量", 3),
    
    // 资金流指标
    MAIN_NET_INFLOW(390, "主力净流入", "主力资金净流入金额", 4),
    LARGE_ORDER_INFLOW(391, "大单净流入", "大单资金净流入金额", 4),
    
    // 波动性指标
    AMPLITUDE(190, "振幅", "当日最高最低点之间的波动幅度", 5),
    TURNOVER_RATE(187, "换手率", "指数成分股总成交量的比例", 5),
    
    // 市场情绪指标
    PE_RATIO(205, "市盈率", "指数整体市盈率水平", 6),
    PB_RATIO(182, "市净率", "指数整体市净率水平", 6);

    private final int indicatorCode;
    private final String name;
    private final String description;
    private final int priority; // 优先级，数值越小优先级越高

    IndexMonitorIndicatorEnum(int indicatorCode, String name, String description, int priority) {
        this.indicatorCode = indicatorCode;
        this.name = name;
        this.description = description;
        this.priority = priority;
    }

    /**
     * 获取风控核心监控指标
     */
    public static IndexMonitorIndicatorEnum[] getCoreRiskIndicators() {
        return new IndexMonitorIndicatorEnum[]{
            DAILY_CHANGE_PERCENT, 
            ADVANCE_DECLINE_RATIO,
            AMPLITUDE,
            MAIN_NET_INFLOW
        };
    }

    /**
     * 通过指标代码获取枚举
     */
    public static IndexMonitorIndicatorEnum getByCode(int code) {
        for (IndexMonitorIndicatorEnum indicator : values()) {
            if (indicator.getIndicatorCode() == code) {
                return indicator;
            }
        }
        return null;
    }
}