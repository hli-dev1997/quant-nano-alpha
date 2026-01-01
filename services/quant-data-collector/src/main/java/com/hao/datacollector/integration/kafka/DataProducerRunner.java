package com.hao.datacollector.integration.kafka;

import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.KafkaProducerService;
import com.hao.datacollector.service.QuotationService;
import integration.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DataProducerRunner implements CommandLineRunner {

    @Autowired
    private KafkaProducerService producerService;

    @Autowired
    private QuotationService quotationService;

    private static final int BATCH_SIZE = 1000; // 每批发送条数，可根据实际情况调整

    /**
     * todo 详细思路说明
     *
     * Topic 统一
     *
     * quotation topic 统一管理所有股票数据，不要为每只股票创建 topic（避免 5000+ topic 导致 Kafka 元数据膨胀）。
     *
     * Key 分区
     *
     * windCode 作为 key 发送，Kafka 根据 key hash 自动分配 partition。
     *
     * 同一只股票的数据永远进入同一 partition → 消息顺序保证。
     *
     * 单条消息发送
     *
     * HistoryTrendDTO 拆分成单条消息发送，不要直接序列化整个 List（避免超大消息导致 Broker 崩溃）。
     *
     * 序列化
     *
     * JSON 简单可用，但生产环境建议 Avro/Protobuf（更节省网络和存储，且向前兼容性好）。
     *
     * 吞吐量优化
     *
     * KafkaProducerService 内部可配置 linger.ms、batch.size 批量发送消息，提高吞吐量。
     *
     * 消费端设计
     *
     * Consumer Group 消费整个 topic，每个 consumer 自动分配 partition，处理分配到的若干股票数据。
     *
     * 可扩展性
     *
     * 随着股票数量增加，只需增加 partition 或者 Consumer 实例，topic 不需要拆分。
     *
     * 新增批量发送说明
     *
     * - 为了直观感受到高性能，这里增加按固定批量大小（BATCH_SIZE）分批发送的逻辑
     * - 每批数据内部依然调用 KafkaProducerService.send()，保证 key 分区顺序
     * - KafkaTemplate 内部会自动根据 batch.size + linger.ms 进行真正的批量发送到 broker
     * - 这样可以减少循环次数，提高发送效率，尤其是面对三千万条历史数据时
     *
     * @param args
     * @throws Exception
     */
    @Override
    public void run(String... args) throws Exception {
        String topic = KafkaTopics.QUOTATION.code(); // Kafka 统一 topic

//        List<HistoryTrendDTO> historyTrendDataByDate = quotationService.getHistoryTrendDataByDate("20250801", null);
//        if (historyTrendDataByDate == null || historyTrendDataByDate.isEmpty()) {
//            log.warn("没有获取到历史行情数据，topic={}", topic);
//            return;
//        }
//
//        log.info("开始发送历史行情数据到_Kafka_topic={}，总条数={}", topic, historyTrendDataByDate.size());

        // ---------------- 批量发送逻辑 ----------------
//        while (true){
//            List<HistoryTrendDTO> batchList = new ArrayList<>(BATCH_SIZE);
//            int totalSent = 0;
//            for (HistoryTrendDTO dto : historyTrendDataByDate) {
//                batchList.add(dto);
//                if (batchList.size() >= BATCH_SIZE) {
//                    sendBatch(topic, batchList); // 批量发送
//                    totalSent += batchList.size();
//                    batchList.clear();
//                }
//            }
//            // 发送剩余不足 BATCH_SIZE 的数据
//            if (!batchList.isEmpty()) {
//                sendBatch(topic, batchList);
//                totalSent += batchList.size();
//            }
//
//            log.info("历史行情数据批量发送完成，总条数={}，topic={}", totalSent, topic);
//        }
    }

    /**
     * 批量发送方法
     * 核心思路：
     * - 遍历批量 DTO 列表，调用 KafkaProducerService.send() 内部异步发送
     * - KafkaTemplate 内部会自动根据 batch.size + linger.ms 进行真正的批量发送到 broker
     *
     * @param topic Kafka topic
     * @param batchData 批量 DTO 列表
     */
    private void sendBatch(String topic, List<HistoryTrendDTO> batchData) {
        for (HistoryTrendDTO dto : batchData) {
            try {
                log.info("批量发送数据:_windCode={},_tradeDate={}", dto.getWindCode(), dto.getTradeDate());
                producerService.send(topic, dto.getWindCode(), dto);
            } catch (Exception e) {
                log.error("批量发送失败:_windCode={},_error={}", dto.getWindCode(), e.getMessage(), e);
            }
        }
    }
}
