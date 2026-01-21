package com.hao.datacollector.replay.preheat;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略数据预热器接口
 *
 * 设计目的：
 * 1. 定义策略预热的统一契约，支持多策略扩展。
 * 2. 在回放启动时被调用，预加载策略所需的历史数据到Redis。
 *
 * 扩展方式：
 * - 新增策略只需实现此接口并添加 @Component 注解
 * - Spring 会自动注入到 StrategyPreheaterManager
 *
 * @author hli
 * @date 2026-01-20
 */
public interface StrategyPreheater {

    /**
     * 获取策略ID
     *
     * @return 策略唯一标识，如 "NINE_TURN", "MACD"
     */
    String getStrategyId();

    /**
     * 预热策略数据
     *
     * @param tradeDate  回放开始日期（策略以此为基准计算所需历史数据）
     * @param stockCodes 股票代码列表（可选，null表示全部）
     * @return 预热成功的股票数量
     */
    int preheat(LocalDate tradeDate, List<String> stockCodes);
}
