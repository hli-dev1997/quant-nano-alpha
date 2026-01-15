package com.hao.strategyengine.integration.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 回放数据消费者
 * <p>
 * 负责接收来自数据采集服务（quant-data-collector）的回放行情数据。
 * 这些数据将用于驱动策略引擎进行历史回测或模拟交易。
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@Component
public class ReplayDataConsumer {

    /**
     * 监听行情主题
     * <p>
     * 这里的 groupId 设置为 "strategy-engine-replay-group"，
     * 确保策略引擎作为一个独立的消费组，不与其他服务冲突。
     *
     * @param record 消息记录
     */
    @KafkaListener(topics = "quotation", groupId = "strategy-engine-replay-group")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            String value = record.value();
            
            // 在这里，你可以将数据反序列化为对象，并传递给策略核心逻辑
            // 例如：strategyCore.process(JsonUtil.toList(value, HistoryTrendDTO.class));

            // 目前仅打印日志以验证接收
            log.info("策略引擎收到回放数据|Strategy_received_replay_data,offset={},valueLength={},content={}",
                    record.offset(), value.length(), value);

        } catch (Exception e) {
            log.error("策略引擎消费回放数据失败|Strategy_consume_error", e);
        }
    }
}
