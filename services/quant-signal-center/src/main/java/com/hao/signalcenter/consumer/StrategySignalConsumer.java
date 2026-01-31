package com.hao.signalcenter.consumer;

import com.hao.signalcenter.model.StockSignal;
import com.hao.signalcenter.service.RiskControlClient;
import com.hao.signalcenter.service.SignalCacheService;
import com.hao.signalcenter.service.SignalPersistenceService;
import dto.StrategySignalDTO;
import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import util.JsonUtil;

/**
 * 策略信号 Kafka 消费者 (Strategy Signal Consumer)
 * <p>
 * 类职责：
 * 消费策略引擎发送的信号，执行完整的信号处理流程：
 * 1. 反序列化消息
 * 2. 旁路查询风控分数
 * 3. 幂等落库 MySQL
 * 4. 主动推送 Redis 缓存
 * 5. 手动确认 Offset
 * <p>
 * 核心设计：
 * - 手动确认模式：确保完整处理后才提交 Offset
 * - 异常处理：处理失败时拒绝确认，等待重新投递
 * - 幂等性：重复消费不会产生重复记录
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Component
public class StrategySignalConsumer {

    @Autowired
    private RiskControlClient riskControlClient;

    @Autowired
    private SignalPersistenceService signalPersistenceService;

    @Autowired
    private SignalCacheService signalCacheService;

    /**
     * 消费策略信号
     * <p>
     * 监听 stock-strategy-signal 主题，处理策略引擎发送的信号。
     *
     * @param message 消息内容（JSON 格式的 StrategySignalDTO）
     * @param ack     Kafka Acknowledgment，用于手动确认
     */
    @KafkaListener(
            topics = KafkaConstants.TOPIC_STRATEGY_SIGNAL,
            groupId = KafkaConstants.GROUP_SIGNAL_CENTER,
            containerFactory = KafkaConstants.LISTENER_CONTAINER_FACTORY
    )
    public void consume(String message, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        StrategySignalDTO signalDTO = null;

        try {
            // 1. 反序列化
            signalDTO = JsonUtil.toBean(message, StrategySignalDTO.class);
            if (signalDTO == null || signalDTO.getWindCode() == null) {
                log.warn("信号反序列化失败_跳过|Signal_deserialize_failed,message={}", message);
                ack.acknowledge();  // 无效消息直接确认
                return;
            }

            log.debug("信号消费开始|Signal_consume_start,code={},strategy={}",
                    signalDTO.getWindCode(), signalDTO.getStrategyName());

            // 2. 旁路查询风控分数（使用新的 API 获取分数和降级标志）
            RiskControlClient.RiskScoreResult riskResult = riskControlClient.getMarketSentimentScoreWithFallback();
            int riskScore = riskResult.getScore();
            boolean isFallback = riskResult.isFallback();

            // 3. 幂等落库
            StockSignal signal = signalPersistenceService.saveSignal(signalDTO, riskScore, isFallback);

            // 4. 主动推送缓存（仅通过的信号）
            signalCacheService.updateSignalCache(signal);

            // 5. 手动确认 Offset
            ack.acknowledge();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("信号处理完成|Signal_processed,code={},strategy={},status={},costMs={}",
                    signalDTO.getWindCode(), signalDTO.getStrategyName(),
                    signal.getShowStatus(), costTime);

        } catch (Exception e) {
            // 处理失败，不确认 Offset，等待重试
            log.error("信号处理失败|Signal_process_failed,code={},strategy={}",
                    signalDTO != null ? signalDTO.getWindCode() : "null",
                    signalDTO != null ? signalDTO.getStrategyName() : "null", e);
            // 注意：不调用 ack.acknowledge()，消息将重新投递
            // 如果实现了死信队列，可以在这里处理
        }
    }
}
