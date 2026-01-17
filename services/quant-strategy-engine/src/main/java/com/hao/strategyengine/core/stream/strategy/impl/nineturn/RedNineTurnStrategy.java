package com.hao.strategyengine.core.stream.strategy.impl.nineturn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 九转序列 - 红九策略 (Bullish Red Nine)
 * <p>
 * 逻辑：连续9天收盘价 < 4天前收盘价
 * 含义：下跌趋势衰竭，预期反弹（买入信号）
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Component
public class RedNineTurnStrategy extends AbstractNineTurnStrategy {

    private static final String STRATEGY_ID = "NINE_TURN_RED";

    @Override
    public String getId() {
        return STRATEGY_ID;
    }

    @Override
    protected boolean checkCondition(double currentPrice, double priceT4) {
        // 红九条件：当前价 < 4天前价格
        return currentPrice < priceT4;
    }
}
