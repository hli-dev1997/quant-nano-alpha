package com.hao.riskcontrol.integration.kafka;

import com.hao.riskcontrol.dto.quotation.IndexQuotationDTO;
import com.hao.riskcontrol.service.MarketSentimentService;
import integration.kafka.KafkaConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

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

    /**
     * 批量消费指数行情消息
     * <p>
     * 每秒可能收到多条不同指数的行情数据，统一交给 MarketSentimentService 处理。
     *
     * @param records 指数行情数据批次
     */
    @KafkaListener(
            topics = KafkaConstants.TOPIC_QUOTATION_INDEX,
            groupId = KafkaConstants.GROUP_RISK_CONTROL,
            containerFactory = KafkaConstants.LISTENER_CONTAINER_FACTORY
    )
    public void consume(@Payload List<IndexQuotationDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        log.debug("收到指数行情消息|Index_quotation_received,count={}", records.size());

        for (IndexQuotationDTO record : records) {
            try {
                marketSentimentService.updateIndexPrice(
                        record.getWindCode(),
                        record.getLatestPrice(),
                        record.getTradeDate()
                );
            } catch (Exception e) {
                log.error("处理指数行情失败|Index_quotation_process_error,windCode={},error={}",
                        record.getWindCode(), e.getMessage(), e);
            }
        }
    }
}
