package com.hao.strategyengine.core.stream.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.strategyengine.core.stream.domain.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 流式分发引擎（Kafka Consumer入口）
 *
 * 设计目的：
 * 1. 作为Kafka消息消费的入口点，解耦消息协议与业务逻辑。
 * 2. 负责将原始消息（JSON/DTO）转换为领域对象Tick。
 * 3. 将Tick分发到StreamComputeEngine进行处理。
 *
 * 字段映射关系（对齐 HistoryTrendDTO）：
 * | JSON 字段       | Tick 字段      | 类型转换                |
 * |-----------------|----------------|------------------------|
 * | windCode        | symbol         | 直接映射               |
 * | latestPrice     | price          | Double → double        |
 * | averagePrice    | averagePrice   | Double → double        |
 * | totalVolume     | volume         | Double → long          |
 * | tradeDate       | eventTime      | LocalDateTime → long   |
 *
 * 消息格式示例：
 * <pre>
 * {
 *   "windCode": "600519.SH",
 *   "tradeDate": "2026-01-02 13:01:01",
 *   "latestPrice": 1800.50,
 *   "totalVolume": 12345,
 *   "averagePrice": 1798.30
 * }
 * </pre>
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamDispatchEngine {

    /**
     * 流式计算引擎
     */
    private final StreamComputeEngine computeEngine;

    /**
     * JSON解析器
     */
    private final ObjectMapper objectMapper;

    /**
     * 日期时间格式（对齐 HistoryTrendDTO 的 @JsonFormat）
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分发Tick对象（直接调用）
     *
     * @param tick 已构建的Tick对象
     */
    public void dispatch(Tick tick) {
        if (tick == null || tick.getSymbol() == null) {
            log.warn("无效的Tick数据_跳过处理|Invalid_tick_data_skipped");
            return;
        }

        computeEngine.process(tick);
    }

    /**
     * 分发原始JSON消息（Kafka Consumer入口）
     *
     * 实现逻辑：
     * 1. 解析JSON为Tick对象（对齐 HistoryTrendDTO 字段）
     * 2. 调用dispatch(Tick)分发
     *
     * @param jsonMessage Kafka原始消息（JSON字符串）
     */
    public void dispatchJson(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.isBlank()) {
            log.debug("空消息_跳过处理|Empty_message_skipped");
            return;
        }

        try {
            // 中文：解析JSON为Tick对象，字段对齐 HistoryTrendDTO
            // English: Parse JSON to Tick object, fields align with HistoryTrendDTO
            Tick tick = parseJsonToTick(jsonMessage);
            dispatch(tick);
        } catch (Exception e) {
            // 中文：解析失败仅记录日志，不抛出异常影响Kafka消费
            // English: Log parse failure, don't throw exception to affect Kafka consumption
            log.error("消息解析失败|Message_parse_failed,message={}", jsonMessage, e);
        }
    }

    /**
     * 解析JSON为Tick对象
     *
     * 主要处理 HistoryTrendDTO 格式，同时兼容其他可能的字段名：
     * - windCode / symbol / stock_code → symbol
     * - latestPrice / price / close → price
     * - averagePrice / avgPrice → averagePrice
     * - totalVolume / volume / vol → volume
     * - tradeDate / eventTime / timestamp → eventTime
     *
     * @param json JSON字符串
     * @return Tick对象
     * @throws Exception 解析异常
     */
    private Tick parseJsonToTick(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);

        // 中文：支持嵌套格式，数据可能在data节点下
        // English: Support nested format, data may be under 'data' node
        JsonNode dataNode = root.has("data") ? root.get("data") : root;

        // 中文：解析股票代码（优先 windCode，兼容其他字段名）
        // English: Parse symbol (prefer windCode, compatible with other field names)
        String symbol = getStringValue(dataNode, "windCode", "symbol", "stock_code", "wind_code");
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("缺少股票代码字段");
        }

        // 中文：解析价格字段
        // English: Parse price fields
        double price = getDoubleValue(dataNode, "latestPrice", "price", "close", "last_price");
        double averagePrice = getDoubleValue(dataNode, "averagePrice", "avgPrice", "average_price", "vwap");

        // 中文：解析成交量（Double → long）
        // English: Parse volume (Double → long)
        long volume = getLongValue(dataNode, "totalVolume", "volume", "vol");

        // 中文：解析时间（LocalDateTime 字符串 → long 时间戳）
        // English: Parse time (LocalDateTime string → long timestamp)
        long eventTime = parseEventTime(dataNode);

        return Tick.builder()
                .symbol(symbol)
                .price(price)
                .averagePrice(averagePrice)
                .volume(volume)
                .eventTime(eventTime)
                .build();
    }

    /**
     * 解析事件时间
     *
     * 支持两种格式：
     * 1. tradeDate: "2026-01-02 13:01:01" (HistoryTrendDTO 格式)
     * 2. eventTime/timestamp: 1735833661000 (毫秒时间戳)
     */
    private long parseEventTime(JsonNode node) {
        // 中文：优先尝试 tradeDate 字符串格式
        // English: First try tradeDate string format
        if (node.has("tradeDate") && !node.get("tradeDate").isNull()) {
            String tradeDateStr = node.get("tradeDate").asText();
            try {
                LocalDateTime ldt = LocalDateTime.parse(tradeDateStr, DATE_TIME_FORMATTER);
                return Tick.toTimestamp(ldt);
            } catch (Exception e) {
                log.debug("tradeDate解析失败_尝试其他格式|tradeDate_parse_failed,value={}", tradeDateStr);
            }
        }

        // 中文：尝试时间戳格式
        // English: Try timestamp format
        long timestamp = getLongValue(node, "eventTime", "event_time", "timestamp", "time");
        if (timestamp > 0) {
            return timestamp;
        }

        // 中文：兜底使用当前时间
        // English: Fallback to current time
        return System.currentTimeMillis();
    }

    /**
     * 从JsonNode获取字符串值，支持多个候选字段名
     */
    private String getStringValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asText();
            }
        }
        return null;
    }

    /**
     * 从JsonNode获取double值，支持多个候选字段名
     */
    private double getDoubleValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asDouble();
            }
        }
        return 0.0;
    }

    /**
     * 从JsonNode获取long值，支持多个候选字段名
     */
    private long getLongValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asLong();
            }
        }
        return 0L;
    }
}
