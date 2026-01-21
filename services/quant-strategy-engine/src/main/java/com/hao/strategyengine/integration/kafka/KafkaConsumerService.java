package com.hao.strategyengine.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.strategyengine.core.stream.strategy.StrategyDispatcher;
import dto.HistoryTrendDTO;
import integration.kafka.KafkaConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka行情消息消费服务
 *
 * 设计目的：
 * 1. 消费Kafka行情消息，解析为通用 DTO。
 * 2. 调用 StrategyDispatcher 异步分发给所有策略。
 * 3. 职责单一：只负责消息接收和解析，策略执行由 StrategyDispatcher 负责。
 *
 * @author hli
 * @date 2026-01-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
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
     * JSON 解析器
     */
    private final ObjectMapper objectMapper;

    /**
     * 策略调度器（统一入口）
     */
    private final StrategyDispatcher strategyDispatcher;

    /**
     * 消费行情消息
     * <p>
     * 实现逻辑：
     * 1. 解析 JSON 为 HistoryTrendDTO
     * 2. 调用 StrategyDispatcher 分发给所有策略
     * 3. 手动提交 offset（无论成功失败）
     *
     * @param record Kafka消息记录
     * @param ack    手动提交句柄
     */
    @KafkaListener(
            topics = KafkaConstants.TOPIC_QUOTATION,
            groupId = "strategy-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String message = null;
        HistoryTrendDTO dto = null;
        try {
            message = record.value();
            long now = System.currentTimeMillis();

            // 增加计数
            int count = counter.incrementAndGet();

            // 每秒输出一次吞吐量统计
            if (now - windowStart >= 1000) {
                log.debug("策略引擎吞吐量|Strategy_throughput,count={}", count);
                counter.set(0);
                windowStart = now;
            }

            // TODO STOP 6: 策略模块Kafka消费入口 - 收到股票行情数据
            // 解析消息为 HistoryTrendDTO
            dto = objectMapper.readValue(message, HistoryTrendDTO.class);

            // 调用策略调度器分发给所有策略（异步并行执行）
            strategyDispatcher.dispatch(dto);

        } catch (Exception e) {
            // 记录详细的异常信息
            String windCode = dto != null ? dto.getWindCode() : "unknown";
            log.error("消息处理异常|Message_processing_error,code={},offset={},partition={},error={}",
                    windCode, record.offset(), record.partition(), e.getMessage(), e);
        } finally {
            // 无论成功失败都提交 offset
            ack.acknowledge();
        }
    }
}
