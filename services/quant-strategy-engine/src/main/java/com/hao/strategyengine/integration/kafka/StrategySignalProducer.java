package com.hao.strategyengine.integration.kafka;

import dto.StrategySignalDTO;
import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import util.JsonUtil;

import java.util.concurrent.CompletableFuture;

/**
 * 策略信号 Kafka 生产者服务 (Strategy Signal Producer)
 * <p>
 * 类职责：
 * 负责将策略触发的信号发送到 Kafka，供信号中心消费处理。
 * <p>
 * 使用场景：
 * 1. BaseStrategy 中触发信号后调用此服务发送消息
 * 2. 策略引擎批量回测时发送历史信号
 * <p>
 * 设计目的：
 * 1. 解耦策略计算与信号处理，策略模块只管计算，不关心后续存储
 * 2. 使用 windCode 作为 partition key，保证同一股票的信号顺序
 * 3. 异步发送提高吞吐量，回调处理发送结果
 *
 * @author hli
 * @date 2026-01-30
 */
@Slf4j
@Service
public class StrategySignalProducer {

    @Autowired
    private KafkaTemplate<String, String> signalKafkaTemplate;

    /**
     * 发送策略信号到 Kafka
     * <p>
     * 使用 windCode 作为 partition key，确保同一股票的信号落在同一分区，
     * 保证信号的顺序性（如先买后卖的顺序不会乱）。
     * <p>
     * 异步发送，通过回调处理发送结果：
     * - 成功：记录 DEBUG 日志
     * - 失败：记录 ERROR 日志（Kafka 配置了无限重试，失败情况极少）
     *
     * @param signal 策略信号 DTO
     */
    public void sendSignal(StrategySignalDTO signal) {
        if (signal == null || signal.getWindCode() == null) {
            log.warn("信号为空_跳过发送|Signal_empty_skip");
            return;
        }

        String json = JsonUtil.toJson(signal);
        String topic = KafkaConstants.TOPIC_STRATEGY_SIGNAL;
        String key = signal.getWindCode();  // 使用股票代码作为 partition key

        // 异步发送
        CompletableFuture<SendResult<String, String>> future =
                signalKafkaTemplate.send(topic, key, json);

        // 处理发送结果
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // 发送失败（极少情况，因为配置了无限重试）
                log.error("信号发送失败|Signal_send_failed,code={},strategy={},error={}",
                        signal.getWindCode(), signal.getStrategyId(), ex.getMessage());
            } else {
                // 发送成功
                if (log.isDebugEnabled()) {
                    log.debug("信号发送成功|Signal_sent,topic={},partition={},offset={},code={},strategy={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            signal.getWindCode(),
                            signal.getStrategyId());
                }
            }
        });

        // 记录发送日志（无论成功失败都记录，便于追踪）
        log.info("信号已投递|Signal_submitted,code={},strategy={},type={},price={}",
                signal.getWindCode(), signal.getStrategyId(),
                signal.getSignalType(), signal.getTriggerPrice());
    }

    /**
     * 同步发送策略信号（阻塞等待结果）
     * <p>
     * 适用于需要确认发送结果的场景，如测试验证。
     * 生产环境建议使用异步发送 {@link #sendSignal(StrategySignalDTO)}。
     *
     * @param signal 策略信号 DTO
     * @return 发送结果，失败返回 null
     */
    public SendResult<String, String> sendSignalSync(StrategySignalDTO signal) {
        if (signal == null || signal.getWindCode() == null) {
            log.warn("信号为空_跳过发送|Signal_empty_skip");
            return null;
        }

        String json = JsonUtil.toJson(signal);
        String topic = KafkaConstants.TOPIC_STRATEGY_SIGNAL;
        String key = signal.getWindCode();

        try {
            SendResult<String, String> result = signalKafkaTemplate.send(topic, key, json).get();
            log.info("信号同步发送成功|Signal_sync_sent,code={},strategy={},offset={}",
                    signal.getWindCode(), signal.getStrategyId(),
                    result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("信号同步发送失败|Signal_sync_failed,code={},strategy={}",
                    signal.getWindCode(), signal.getStrategyId(), e);
            return null;
        }
    }
}
