package com.hao.quant.stocklist.controller.vo;

import lombok.Builder;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 类说明 / Class Description:
 * 中文：返回给前端的股票视图对象。
 * English: Stock view object returned to frontend.
 *
 * 设计目的 / Design Purpose:
 * 中文：通过值对象封装，避免外部修改内部结构。VO 放在 controller 下，表示是展示层的数据结构。
 * English: Encapsulated as value object to prevent external modification. VO under controller indicates presentation layer data structure.
 */
@Value
@Builder
public class StablePicksVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 策略标识 */
    String strategyId;
    /** 股票代码 */
    String stockCode;
    /** 股票名称 */
    String stockName;
    /** 所属行业 */
    String industry;
    /** 综合得分 */
    BigDecimal score;
    /** 排名 */
    Integer ranking;
    /** 流通市值 */
    BigDecimal marketCap;
    /** 市盈率 */
    BigDecimal peRatio;
    /** 交易日期 */
    LocalDate tradeDate;
    /** 扩展信息 JSON 字符串 */
    String extraData;
}
