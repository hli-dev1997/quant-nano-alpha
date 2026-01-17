package com.quant.data.archive.integration.kafka;

import integration.kafka.KafkaConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 行情消息消费服务
 *
 * 设计目的：
 * 1. 消费行情主题并输出吞吐统计。
 * 2. 提供简单的消费窗口统计能力。
 *
 * 为什么需要该类：
 * - 用于验证消费速率与消费链路稳定性。
 *
 * 核心实现思路：
 * - 以1秒为窗口统计消息数与首尾消息。
 * - 手动确认offset控制重试策略。
 */
@Slf4j
@Service
public class ConsumerService {

    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile long windowStart = System.currentTimeMillis();
    private volatile String firstMessage = null;
    private volatile String lastMessage = null;

    private final ThreadPoolTaskExecutor ioTaskExecutor;

    /**
     * 构造消费服务
     *
     * 实现逻辑：
     * 1. 注入IO线程池用于后续扩展异步处理。
     *
     * @param ioTaskExecutor IO线程池
     */
    public ConsumerService(@Qualifier("ioTaskExecutor") ThreadPoolTaskExecutor ioTaskExecutor) {
        this.ioTaskExecutor = ioTaskExecutor;
    }

    @KafkaListener(
            topics = KafkaConstants.TOPIC_QUOTATION,
            groupId = KafkaConstants.GROUP_DATA_ARCHIVE,
            containerFactory = KafkaConstants.LISTENER_CONTAINER_FACTORY
    )
    /**
     * 消费行情消息并输出统计
     *
     * 实现逻辑：
     * 1. 更新窗口内首条与末条消息。
     * 2. 每秒输出一次统计并重置窗口。
     * 3. 处理成功后手动确认offset。
     *
     * @param message 消息内容
     * @param ack 手动确认器
     */
    public void consume(String message, Acknowledgment ack) {  // 注意这里使用手动确认模式
        // 实现思路：
        // 1. 统计窗口内首尾消息与吞吐。
        // 2. 处理完成后确认offset。
        try {
            long now = System.currentTimeMillis();
            // 记录第一条消息
            if (firstMessage == null) {
                firstMessage = message;
            }
            // 每条消息都更新 lastMessage
            lastMessage = message;
            // 增加计数
            int count = counter.incrementAndGet();
            // 每秒输出一次统计
            if (now - windowStart >= 1000) {
                log.info("消费统计|Consume_stats,threadName={},qps={},firstMessage={},lastMessage={}",
                        Thread.currentThread().getName(),
                        count,
                        firstMessage,
                        lastMessage);
                // 重置计数器和窗口
                counter.set(0);
                firstMessage = null;
                lastMessage = null;
                windowStart = now;
            }
            // 手动提交 offset
            ack.acknowledge();
        } catch (Exception e) {
            log.error("消息处理异常|Message_handle_error", e);
            // 不提交 offset，消息会重试
        }
        // TODO: 这里可以根据windCode解析消息JSON，然后处理策略
    }
}
