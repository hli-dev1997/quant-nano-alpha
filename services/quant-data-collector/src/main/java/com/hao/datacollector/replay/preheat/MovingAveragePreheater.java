package com.hao.datacollector.replay.preheat;

import com.hao.datacollector.service.StrategyPreparationService;
import enums.strategy.StrategyMetaEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 多周期均线策略预热器
 * <p>
 * 职责：
 * 预热多周期均线策略所需的前59个交易日收盘价数据。
 * 用于策略引擎实时计算 MA5/MA20/MA60 多头/空头排列信号。
 * <p>
 * 多头排列 (Bullish)：MA5 > MA20 > MA60 → 强劲上涨趋势
 * 空头排列 (Bearish)：MA5 < MA20 < MA60 → 强劲下跌趋势
 *
 * @author hli
 * @date 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MovingAveragePreheater implements StrategyPreheater {

    private final StrategyPreparationService preparationService;

    @Override
    public String getStrategyId() {
        return StrategyMetaEnum.PREHEATER_MOVING_AVERAGE.getId();
    }

    @Override
    public int preheat(LocalDate tradeDate, List<String> stockCodes) {
        log.info("均线预热开始|MA_preheat_start,tradeDate={}", tradeDate);
        return preparationService.prepareMovingAverageData(tradeDate, stockCodes);
    }
}
