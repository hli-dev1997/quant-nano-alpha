package com.hao.datacollector.replay.preheat;

import com.hao.datacollector.service.StrategyPreparationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 九转策略预热器
 *
 * 职责：
 * 预热九转序列策略所需的前13个交易日收盘价数据。
 *
 * @author hli
 * @date 2026-01-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NineTurnPreheater implements StrategyPreheater {

    private static final String STRATEGY_ID = "NINE_TURN";

    private final StrategyPreparationService preparationService;

    @Override
    public String getStrategyId() {
        return STRATEGY_ID;
    }

    @Override
    public int preheat(LocalDate tradeDate, List<String> stockCodes) {
        // 调用带 stockCodes 的重载方法，实现按指定股票或全量预热
        return preparationService.prepareNineTurnData(tradeDate, stockCodes);
    }
}
