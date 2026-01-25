package com.hao.riskcontrol.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.riskcontrol.dto.quotation.IndexQuotationDTO;
import com.hao.riskcontrol.service.MarketSentimentService;
import integration.kafka.KafkaConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 指数行情 Kafka 消费者
 * <p>
 * 接收数据采集模块推送的指数行情数据，用于实时计算市场情绪评分。
 * 消费 Kafka Topic: quotation-index
 *
 * @author hli
 * @date 2026-01-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexQuotationConsumer {

    private final MarketSentimentService marketSentimentService;
    private final ObjectMapper objectMapper;

    /**
     * 消费指数行情消息
     * <p>
     * 实现逻辑：
     * 1. 接收 Kafka 消息（JSON 字符串）
     * 2. 使用 ObjectMapper 解析为 IndexQuotationDTO
     * 3. 调用 MarketSentimentService 更新指数价格
     * 4. 手动提交 offset
     *
     * @param record Kafka 消息记录
     * @param ack    手动提交句柄
     */
    @KafkaListener(
            topics = KafkaConstants.TOPIC_QUOTATION_INDEX,
            groupId = KafkaConstants.GROUP_RISK_CONTROL,
            containerFactory = KafkaConstants.LISTENER_CONTAINER_FACTORY
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        IndexQuotationDTO dto = null;
        try {
            String message = record.value();

            // 解析 JSON 为 IndexQuotationDTO
            dto = objectMapper.readValue(message, IndexQuotationDTO.class);
            log.info("收到指数行情消息|Index_quotation_received,dto={}", objectMapper.writeValueAsString(dto));

            // 更新指数价格（直接传递 DTO 对象）
            marketSentimentService.updateIndexPrice(dto);
        } catch (Exception e) {
            String windCode = dto != null ? dto.getWindCode() : "unknown";
            log.error("处理指数行情失败|Index_quotation_process_error,windCode={},offset={},partition={},error={}",
                    windCode, record.offset(), record.partition(), e.getMessage(), e);
        } finally {
            // 无论成功失败都提交 offset
            ack.acknowledge();
        }
    }
}
