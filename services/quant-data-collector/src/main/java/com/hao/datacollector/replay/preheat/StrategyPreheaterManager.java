package com.hao.datacollector.replay.preheat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略预热管理器
 *
 * 设计目的：
 * 1. 统一管理所有策略预热器，在回放启动时批量调用。
 * 2. 支持扩展：新增策略只需实现 StrategyPreheater 接口。
 *
 * 实现思路：
 * - 通过 Spring 自动注入所有 StrategyPreheater 实现
 * - 提供 preheatAll 方法批量执行预热
 *
 * @author hli
 * @date 2026-01-20
 */
@Slf4j
@Component
public class StrategyPreheaterManager {

    private final List<StrategyPreheater> preheaters;

    /**
     * 构造函数，Spring 自动注入所有 StrategyPreheater 实现
     */
    public StrategyPreheaterManager(List<StrategyPreheater> preheaters) {
        this.preheaters = preheaters;
        log.info("策略预热管理器初始化|StrategyPreheaterManager_init,preheaterCount={}", 
                preheaters != null ? preheaters.size() : 0);
    }

    /**
     * 预热所有策略数据
     *
     * @param tradeDate  回放开始日期
     * @param stockCodes 股票代码列表（可选）
     */
    public void preheatAll(LocalDate tradeDate, List<String> stockCodes) {
        if (preheaters == null || preheaters.isEmpty()) {
            log.warn("无策略预热器，跳过预热|No_preheaters_skipped");
            return;
        }

        log.info("开始预热所有策略数据|Preheat_all_start,date={},strategies={}", 
                tradeDate, preheaters.stream().map(StrategyPreheater::getStrategyId).toList());

        int totalCount = 0;
        for (StrategyPreheater preheater : preheaters) {
            try {
                log.info("预热策略|Preheat_strategy,id={}", preheater.getStrategyId());
                int count = preheater.preheat(tradeDate, stockCodes);
                totalCount += count;
                log.info("策略预热完成|Preheat_done,id={},count={}", preheater.getStrategyId(), count);
            } catch (Exception e) {
                log.error("策略预热失败|Preheat_failed,id={}", preheater.getStrategyId(), e);
            }
        }

        log.info("所有策略预热完成|Preheat_all_done,totalCount={}", totalCount);
    }
}
