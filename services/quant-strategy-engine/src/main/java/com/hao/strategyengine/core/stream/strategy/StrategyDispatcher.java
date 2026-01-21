package com.hao.strategyengine.core.stream.strategy;

import dto.HistoryTrendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 策略调度器
 * <p>
 * 设计目的：
 * 1. 作为策略执行的统一入口，接收行情数据并分发给所有策略。
 * 2. 使用线程池异步执行各策略判断，避免阻塞 Kafka 消费线程。
 * 3. 策略自动发现：Spring 会将所有 BaseStrategy 实现自动注入。
 * <p>
 * 架构优势：
 * - 职责分离：KafkaConsumerService 只负责接收消息，本类负责策略调度
 * - 易扩展：新增策略只需实现 BaseStrategy 接口
 * - 并行执行：所有策略在线程池中并行判断
 *
 * @author hli
 * @date 2026-01-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyDispatcher {

    /**
     * 所有策略实现（Spring 自动注入）
     */
    private final List<BaseStrategy> strategies;

    /**
     * 策略执行线程池
     */
    @Qualifier("strategyExecutor")
    private final ThreadPoolTaskExecutor strategyExecutor;

    /**
     * 分发行情数据到所有策略
     * <p>
     * 遍历所有注册的策略，使用线程池异步执行判断。
     * 每个策略的 isMatch() 方法会被并行调用。
     *
     * @param dto 分时行情数据
     */
    public void dispatch(HistoryTrendDTO dto) {
        if (dto == null) {
            log.debug("忽略空数据|Ignore_null_dto");
            return;
        }

        for (BaseStrategy strategy : strategies) {
            strategyExecutor.execute(() -> executeStrategy(strategy, dto));
        }
    }

    /**
     * 执行单个策略
     * <p>
     * 捕获策略执行中的所有异常，确保一个策略失败不影响其他策略。
     *
     * @param strategy 策略实例
     * @param dto      行情数据
     */
    private void executeStrategy(BaseStrategy strategy, HistoryTrendDTO dto) {
        try {
            boolean matched = strategy.isMatch(dto);
            if (matched) {
                strategy.onSignalTriggered(dto);
            }
        } catch (Exception e) {
            log.error("策略执行异常|Strategy_execution_error,strategy={},code={},error={}",
                    strategy.getId(), dto.getWindCode(), e.getMessage(), e);
        }
    }

    /**
     * 获取已注册的策略数量
     *
     * @return 策略数量
     */
    public int getStrategyCount() {
        return strategies.size();
    }

    /**
     * 获取所有策略ID
     *
     * @return 策略ID列表
     */
    public List<String> getStrategyIds() {
        return strategies.stream()
                .map(BaseStrategy::getId)
                .toList();
    }
}
