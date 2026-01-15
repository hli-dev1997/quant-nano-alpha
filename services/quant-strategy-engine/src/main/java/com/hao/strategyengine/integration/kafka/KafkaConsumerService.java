package com.hao.strategyengine.integration.kafka;

import com.hao.strategyengine.core.stream.engine.StreamDispatchEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka行情消息消费服务
 *
 * 设计目的：
 * 1. 消费Kafka行情消息，驱动流式计算引擎。
 * 2. 统计消息吞吐量，便于监控和性能调优。
 * 3. 通过StreamDispatchEngine解耦消息消费与业务处理。
 *
 * 实现思路：
 * - 接收消息后立即分发到StreamDispatchEngine
 * - StreamDispatchEngine负责解析和路由到Worker线程
 * - 采用手动ACK模式，保证消息可靠消费
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Service
public class KafkaConsumerService {

    /**
     * 消息计数器（每秒窗口）
     */
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 窗口开始时间
     */
    private volatile long windowStart = System.currentTimeMillis();

    /**
     * 本窗口第一条消息（用于日志采样）
     */
    private volatile String firstMessage = null;

    /**
     * 本窗口最后一条消息（用于日志采样）
     */
    private volatile String lastMessage = null;

    /**
     * 流式分发引擎
     * 负责将消息解析为Tick并分发到计算引擎
     */
    private final StreamDispatchEngine streamDispatchEngine;

    @Autowired
    public KafkaConsumerService(StreamDispatchEngine streamDispatchEngine) {
        this.streamDispatchEngine = streamDispatchEngine;
    }

    /**
     * 消费行情消息
     *
     * 实现逻辑：
     * 1. 更新吞吐量统计（每秒输出一次）
     * 2. 将消息分发到流式计算引擎
     * 3. 手动提交offset
     *
     * 异常处理：
     * - 消费异常不提交offset，消息将被重试
     * - StreamDispatchEngine内部已做异常隔离
     *
     * @param message Kafka消息（JSON格式）
     * @param ack     手动提交句柄
     */
    @KafkaListener(
            topics = "quotation",
            groupId = "strategy-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) {
        try {
            long now = System.currentTimeMillis();

            // 中文：记录第一条消息用于日志采样
            // English: Record first message for log sampling
            if (firstMessage == null) {
                firstMessage = message;
            }
            // 中文：每条消息都更新lastMessage
            // English: Update lastMessage for each message
            lastMessage = message;

            // 中文：增加计数
            // English: Increment counter
            int count = counter.incrementAndGet();

            // 中文：每秒输出一次吞吐量统计
            // English: Output throughput stats every second
            if (now - windowStart >= 1000) {
                //todo 暂时不统计
//                log.info("Kafka消费统计|Kafka_consume_stats,threadName={},throughput={}条/s", Thread.currentThread().getName(), count);
                // 中文：重置计数器和窗口
                // English: Reset counter and window
                counter.set(0);
                firstMessage = null;
                lastMessage = null;
                windowStart = now;
            }

            // 中文：分发消息到流式计算引擎（核心变更）
            // English: Dispatch message to stream compute engine (core change)
            streamDispatchEngine.dispatchJson(message);

            // 中文：手动提交offset
            // English: Manual commit offset
            ack.acknowledge();
        } catch (Exception e) {
            log.error("消息处理异常|Message_processing_error", e);
            // 中文：不提交offset，消息会被重试
            // English: Don't commit offset, message will be retried
        }
    }
}
