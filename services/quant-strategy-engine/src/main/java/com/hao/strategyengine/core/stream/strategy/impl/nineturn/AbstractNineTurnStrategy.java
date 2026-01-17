package com.hao.strategyengine.core.stream.strategy.impl.nineturn;

import com.hao.strategyengine.core.stream.domain.StockDomainContext;
import com.hao.strategyengine.core.stream.domain.Tick;
import com.hao.strategyengine.core.stream.strategy.StreamingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * 九转序列策略基类 (Abstract Nine Turn Strategy)
 * <p>
 * 封装了九转序列的核心状态管理和通用流程。
 * 子类只需实现具体的红九（买入）或绿九（卖出）判断逻辑。
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
public abstract class AbstractNineTurnStrategy implements StreamingStrategy {

    private static final ZoneOffset BEIJING_ZONE = ZoneOffset.of("+8");
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 0, 0);

    /**
     * 九转所需的回溯天数 (前13个交易日数据)用第14个交易日的收盘价作为最终判断
     */
    protected static final int LOOKBACK_DAYS = 13;

    /**
     * 触发信号的连续计数阈值 (9)
     */
    protected static final int TRIGGER_COUNT = 9;

    /**
     * 获取策略唯一标识
     */
    @Override
    public abstract String getId();

    /**
     * 具体的条件判断逻辑 (由子类实现)
     *
     * @param currentPrice 当前收盘价
     * @param priceT4      4天前的收盘价
     * @return true if condition met
     */
    protected abstract boolean checkCondition(double currentPrice, double priceT4);

    /**
     * 获取计数器属性 Key
     */
    protected String getCountKey() {
        return getId() + "_COUNT";
    }

    @Override
    public boolean isMatch(Tick tick, StockDomainContext context) {
        // 1. 时间检查：必须是收盘数据 (>= 15:00:00)
        // TODO: 2.当前交易日必须是历史缓存辩识数据的下一个交易日数据才可以链接
        // 注意：Tick 中的 eventTime 是毫秒时间戳
        if (!isMarketClose(tick.getEventTime())) {
            if (log.isDebugEnabled()) {
                log.debug("非收盘时间_跳过计算|Not_market_close,strategy={}", getId());
            }
            return false;
        }

        // TODO: bug 2. 检查历史数据是否充足
        if (!context.hasEnoughHistory(LOOKBACK_DAYS)) {
            log.debug("数据不足_跳过{}计算|Insufficient_data,symbol={},size={}", getId(), context.getSymbol(), context.getSize());
            return false;
        }

        // 3. 获取价格数据
        double priceT4 = context.getPrice(LOOKBACK_DAYS);
        double currentPrice = tick.getPrice();

        // 4. 获取当前计数器
        String countKey = getCountKey();
        int currentCount = context.getStrategyPropOrDefault(countKey, 0);

        // 5. 判断条件
        if (checkCondition(currentPrice, priceT4)) {
            currentCount++;
            context.setStrategyProp(countKey, currentCount);

            log.debug("{}计数更新|Count_update,symbol={},count={},current={},T4={}",
                    getId(), context.getSymbol(), currentCount, currentPrice, priceT4);

            // 6. 触发信号
            if (currentCount >= TRIGGER_COUNT) {
                log.info("{}信号触发|Signal_triggered,symbol={},count={},price={}",
                        getId(), context.getSymbol(), currentCount, currentPrice);
                // 重置计数器，避免重复触发
                context.setStrategyProp(countKey, 0);
                return true;
            }
        } else {
            // 不满足条件，计数器归零
            if (currentCount > 0) {
                log.debug("{}计数重置|Count_reset,symbol={},prevCount={}",
                        getId(), context.getSymbol(), currentCount);
            }
            context.setStrategyProp(countKey, 0);
        }

        return false;
    }

    /**
     * 判断是否为收盘时间 (>= 15:00:00)
     */
    private boolean isMarketClose(long eventTimeMillis) {
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(eventTimeMillis), BEIJING_ZONE);
        LocalTime localTime = time.toLocalTime();
        return !localTime.isBefore(MARKET_CLOSE_TIME);
    }
}
