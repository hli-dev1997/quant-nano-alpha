package com.hao.datacollector.replay.preheat;

import com.hao.datacollector.service.StrategyPreparationService;
import enums.strategy.StrategyMetaEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * DMI 趋向指标策略预热器
 * <p>
 * 职责：
 * 预热 DMI（Directional Movement Index）策略所需的前60个交易日 OHLC 数据。
 * <p>
 * DMI 指标说明：
 * - +DI（上升动向指标）：衡量上涨动能
 * - -DI（下降动向指标）：衡量下跌动能
 * - ADX（平均趋向指数）：衡量趋势强度
 * <p>
 * 数据需求：
 * - 需要60个交易日的最高价、最低价、收盘价（OHLC）
 * - 数据格式：Map&lt;股票代码, List&lt;DailyOhlcDTO&gt;&gt;
 *
 * @author hli
 * @date 2026-02-04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmiPreheater implements StrategyPreheater {

    private final StrategyPreparationService preparationService;

    @Override
    public String getStrategyId() {
        return StrategyMetaEnum.PREHEATER_DMI.getId();
    }

    @Override
    public int preheat(LocalDate tradeDate, List<String> stockCodes) {
        log.info("DMI预热器启动|DMI_preheater_start,tradeDate={},stockCount={}",
                tradeDate, stockCodes != null ? stockCodes.size() : "all");
        return preparationService.prepareDmiData(tradeDate, stockCodes);
    }
}
