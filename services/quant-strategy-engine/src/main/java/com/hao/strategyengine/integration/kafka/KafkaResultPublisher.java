package com.hao.strategyengine.integration.kafka;

import com.alibaba.fastjson.JSON;
import com.hao.strategyengine.common.model.response.StrategyResultBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka策略结果发布器
 *
 * <p>设计目的：
 * 1. 统一封装策略执行结果的Kafka发布逻辑。
 * 2. 提供类型安全的消息发送接口。
 *
 * <p>为什么需要该类：
 * - 策略引擎执行完成后需要将结果推送到Kafka供下游服务消费。
 * - 统一封装可避免业务代码直接依赖KafkaTemplate。
 *
 * <p>核心实现思路：
 * - 使用KafkaTemplate进行消息发送。
 * - 以comboKey作为消息Key，确保相同策略结果路由到同一分区。
 *
 * @author hli
 * @since 2025-10-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaResultPublisher {
    
    private final KafkaTemplate<String, String> kafka;

    /**
     * 发布策略执行结果到Kafka
     *
     * <p>实现逻辑：
     * 1. 将结果对象序列化为JSON字符串。
     * 2. 以comboKey作为分区键发送到指定主题。
     *
     * @param topic  目标主题
     * @param bundle 策略结果包
     */
    public void publish(String topic, StrategyResultBundle bundle) {
        // 使用comboKey作为分区键，确保相同策略结果路由到同一分区
        String key = bundle.getComboKey();
        String value = JSON.toJSONString(bundle);
        
        kafka.send(topic, key, value);
        log.debug("策略结果已发布|Strategy_result_published,topic={},key={}", topic, key);
    }
}
