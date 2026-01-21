# 分布式日志接入 Kafka 指南

本文档旨在为 `quant-nano-alpha` 项目的所有微服务提供一个标准化的日志接入 Kafka 的操作指南。通过该方案，所有服务的日志将被结构化地发送到 Kafka，并由 `quant-data-archive` 服务统一消费，为后续的日志监控、告警和 ELK/EFK 数据分析平台提供统一的数据源。

## 1. 核心设计

- **日志框架**：`SLF4J` + `Logback`
- **日志格式**：`JSON` 格式，便于机器解析
- **传输通道**：`Kafka`，作为所有日志的缓冲队列
- **配置中心**：`Nacos`，动态管理 Kafka Topic、服务器地址等配置
- **消费与归档**：`quant-data-archive` 服务作为唯一的消费者，负责将日志从 Kafka 转移到最终的存储（如 Elasticsearch）。

## 2. 接入流程概览

一个新微服务（以 `your-new-service` 为例）接入日志系统的完整流程如下：

![日志接入流程图](https://mermaid.ink/svg/eyJjb2RlIjoiZ3JhcGggVERcbiAgICBTVkNbeyd5b3VyLW5ldy1zZXJ2aWNlJ31dIC0tPnxMb2dnZXIuSlNPTiBmb3JtYXR8IEtBUEtBX0FQUEVOREVSW2xvZ2JhY2stLXRvLWthZmthXVxuICAgIEtBUEtBX0FQUEVOREVSIC0tPnxQcm9kdWNlciBzZW5kcyBtZXNzYWdlfCBLQUZLQV9DTFVTVEVSKChLYWZrYSBDbHVzdGVyKSlcbiAgICBLQUZLQV9DTFVTVEVSIC0tPnxDb25zdW1lciByZWNlaXZlcyBtZXNzYWdlfCBBVENISVZFSU5HX1NWQ1txdWFudC1kYXRhLWFyY2hpdmVdXG4gICAgQVRDSElWSU5HX1NWQyAtLT58SW5kZXggbG9ncyB0byBFU3xFUyhFbGFzdGljc2VhcmNoKVxuXG4gICAgTmFjb3MoTmFjb3MpIC0uLT58RFlOQU1JQ19DT05GSUd8IEtBUEtBX0FQUEVOREVSXG4gICAgTmFjb3MgLS4tPnxEWU5BTUlDX0NPTkZJR3wgQVRDSElWSU5HX1NWQ1xuIiwibWVybWFpZCI6eyJ0aGVtZSI6ImRlZmF1bHQifSwidXBkYXRlRWRpdG9yIjpmYWxzZX0)

## 3. 标准接入步骤

以 `quant-risk-control` 服务为例，展示如何为一个微服务配置 Kafka 日志。

### 步骤 1：添加 Maven 依赖

确保在你的微服务 `pom.xml` 中包含以下核心依赖：

```xml
<!-- Kafka 日志输出 -->
<dependency>
    <groupId>com.github.danielwegener</groupId>
    <artifactId>logback-kafka-appender</artifactId>
    <version>0.2.0-RC2</version>
</dependency>

<!-- 将日志格式化为 JSON -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**代码位置**：`services/quant-risk-control/pom.xml`

### 步骤 2：配置 Logback

在 `src/main/resources` 目录下创建 `logback.xml` 和 `kafka-appender.xml`。

#### `logback.xml`
此文件作为主入口，通过 `<include>` 引入 Kafka 的配置。

```xml
<configuration>
    <!-- 引入 Kafka Appender 的具体配置 -->
    <include resource="kafka-appender.xml"/>

    <root level="INFO">
        <appender-ref ref="kafkaAppender"/>
        <!-- 其他 Appender，如 console -->
    </root>
</configuration>
```
**代码位置**：`services/quant-risk-control/src/main/resources/logback.xml`

#### `kafka-appender.xml`
这是 Kafka 日志推送的核心配置。

```xml
<included>
    <!-- 动态从 Spring 环境获取配置 -->
    <springProperty scope="context" name="env" source="spring.profiles.active" defaultValue="dev"/>
    <springProperty scope="context" name="service" source="spring.application.name" defaultValue="unknown-service"/>
    <springProperty scope="context" name="hostIp" source="spring.cloud.client.ip-address" defaultValue="unknown"/>
    <springProperty scope="context" name="hostPort" source="server.port" defaultValue="0"/>
    <springProperty scope="context" name="topic" source="logging.kafka.topic" defaultValue="log-default"/>
    <springProperty scope="context" name="bootstrapServers" source="spring.kafka.bootstrap-servers" defaultValue="localhost:9092"/>

    <appender name="kafkaAppender" class="com.github.danielwegener.logback.kafka.KafkaAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <provider class="net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider">
                <pattern>
                    {
                        "env": "${env}",
                        "service": "${service}",
                        "ip": "${hostIp}",
                        "port": "${hostPort}",
                        "level": "%level",
                        "thread": "%thread",
                        "logger": "%logger{36}",
                        "timestamp": "%date{yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}",
                        "message": "%msg",
                        "exception": "%exception"
                    }
                </pattern>
            </provider>
        </encoder>
        <topic>${topic}</topic>
        <addProducerConfig>bootstrap.servers=${bootstrapServers}</addProducerConfig>
        <!-- 其他 Kafka 生产者配置 -->
    </appender>
</included>
```
**代码位置**：`services/quant-risk-control/src/main/resources/kafka-appender.xml`

### 步骤 3：在 Nacos 中配置日志 Topic

在 Nacos 配置中心为你的微服务（例如 `quant-risk-control`）的配置文件（如 `quant-risk-control-dev.yml`）中添加以下配置：

```yaml
logging:
  kafka:
    topic: log-quant-risk-control # 必须与 KafkaConstants 中定义的一致
```

### 步骤 4：动态更新 Logback 配置（核心）

为了在应用启动后，将 Nacos 中获取到的 `hostIp`、`topic` 等动态信息更新到 Logback 中，需要一个配置类。

```java
package com.hao.riskcontrol.web.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.danielwegener.logback.kafka.KafkaAppender;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaLogbackConfig {

    @Value("${spring.cloud.client.ip-address}")
    private String hostIp;
    // ... 其他属性

    @PostConstruct
    public void updateKafkaAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // 遍历所有 logger
        for (ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            Appender<ILoggingEvent> appender = logger.getAppender("kafkaAppender");
            if (appender instanceof KafkaAppender) {
                KafkaAppender<?, ?> kafkaAppender = (KafkaAppender<?, ?>) appender;
                
                // 停止 appender 以安全更新
                kafkaAppender.stop();
                
                // 动态更新 JSON Encoder 中的 IP 等信息
                // ...
                
                // 重启 appender
                kafkaAppender.start();
                log.info("Kafka配置更新成功|Kafka_logback_update_done");
            }
        }
    }
}
```
**代码位置**：`services/quant-risk-control/src/main/java/com/hao/riskcontrol/web/config/KafkaLogbackConfig.java`

### 步骤 5：统一日志常量定义

确保所有 Kafka 主题都在 `common` 模块中统一定义，避免在代码中使用“魔法字符串”。

```java
// common/src/main/java/integration/kafka/KafkaConstants.java
public final class KafkaConstants {
    public static final String TOPIC_LOG_QUANT_RISK_CONTROL = "log-quant-risk-control";
    // ... 其他主题
}
```

## 4. 消费端实现

日志的消费端位于 `quant-data-archive` 服务中。

### 消费监听器

`LogConsumerService` 使用 `@KafkaListener` 注解来订阅所有服务的日志主题。

```java
// services/quant-data-archive/src/main/java/com/quant/data/archive/integration/kafka/LogConsumerService.java
@Service
public class LogConsumerService {

    @KafkaListener(
            topics = {
                    KafkaConstants.TOPIC_LOG_QUANT_XXL_JOB,
                    KafkaConstants.TOPIC_LOG_QUANT_DATA_COLLECTOR,
                    KafkaConstants.TOPIC_LOG_QUANT_STRATEGY_ENGINE,
                    KafkaConstants.TOPIC_LOG_QUANT_RISK_CONTROL,
                    KafkaConstants.TOPIC_LOG_QUANT_STOCK_LIST,
                    KafkaConstants.TOPIC_LOG_QUANT_DATA_ARCHIVE
            },
            groupId = KafkaConstants.GROUP_DATA_ARCHIVE
    )
    public void consumeLog(ConsumerRecord<String, String> record, Acknowledgment ack) {
        // ...
        processLogMessage(logMessage);
        // ...
    }

    private void processLogMessage(LogMessage logMessage) {
        log.info("收到日志|Log_received,instanceId={}, ...", logMessage.getInstanceId());
        
        // TODO: [ES接入] 在此处实现将日志批量写入 Elasticsearch 的逻辑
    }
}
```
**代码位置**：`services/quant-data-archive/src/main/java/com/quant/data/archive/integration/kafka/LogConsumerService.java`

通过以上步骤，一个新服务就可以无缝地将其日志流对接到全项目的日志中心，实现了高效、可扩展的分布式日志管理。
