package com.hao.strategyengine.core.stream.strategy.impl.nineturn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 九转序列 - 绿九策略 (Bearish Green Nine)
 * <p>
 * 逻辑：连续9天收盘价 > 4天前收盘价
 * 含义：上涨趋势衰竭，预期回调（卖出信号/风险提示）
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Component
public class GreenNineTurnStrategy extends AbstractNineTurnStrategy {

    private static final String STRATEGY_ID = "NINE_TURN_GREEN";

    @Override
    public String getId() {
        return STRATEGY_ID;
    }

    @Override
    protected boolean checkCondition(double currentPrice, double priceT4) {
        // 绿九条件：当前价 > 4天前价格
        return currentPrice > priceT4;
    }
}
