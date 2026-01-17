# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
本文件为 Claude Code (claude.ai/code) 在处理此仓库代码时提供指导。

## 1. Project Overview (项目概览)

**Quant Nano Alpha** is a distributed quantitative trading system built on Spring Cloud Alibaba microservices architecture.
**Quant Nano Alpha** 是一个基于 Spring Cloud Alibaba 微服务架构的分布式量化交易系统。

- **Core Concepts (核心概念)**:
  - **Quant**: Quantitative trading (量化交易)
  - **Nano**: Small capital scale focus (极小资金规模)
  - **Alpha**: Pursuit of excess returns (追求超额收益)

## 2. Technology Stack (技术栈)

- **Language**: Java 21
- **Frameworks**:
  - Spring Boot 3.5.3
  - Spring Cloud 2025.0.0
  - Spring Cloud Alibaba 2023.0.3.3
- **Middleware (中间件)**:
  - **Nacos**: Service registry & Configuration (服务注册与配置中心)
  - **Sentinel**: Circuit breaking & Flow control (熔断与限流)
  - **Seata**: Distributed transactions (分布式事务)
  - **XXL-JOB**: Distributed scheduling (分布式任务调度)
  - **Gateway**: Spring Cloud Gateway
  - **Kafka**: Message Queue for data & logs (消息队列：行情与日志)
- **Persistence**: MySQL (Partitioned tables), Redis, Caffeine (L1 Cache)
- **Monitoring**: Micrometer, Prometheus, Grafana

## 3. Project Structure (项目结构)

```text
quant-nano-alpha/
├── common/                     # Shared Library (公共模块)
│   ├── constants/              # System constants (Common, Redis, Kafka, DateTime)
│   ├── dto/                    # Data Transfer Objects
│   ├── enums/                  # System Enums (Strategy, Market, etc.)
│   ├── exception/              # Global Exceptions (BusinessException)
│   ├── integration/            # Middleware integration (Kafka, Redis utils)
│   ├── util/                   # Utilities (DateUtil, JsonUtil, AesEncryptUtil)
│   └── docs/                   # Architecture Docs (架构文档)
├── services/                   # Business Services (业务服务)
│   ├── quant-data-collector/   # Market Data & Replay (行情采集与回放)
│   ├── quant-strategy-engine/  # Strategy Execution (策略计算核心)
│   ├── quant-risk-control/     # Risk Management (风控服务)
│   ├── quant-stock-list/       # High-concurrency Stock Query (高并发选股结果查询)
│   ├── quant-data-archive/     # Log Consumer & Archiving (日志消费与归档)
│   └── quant-xxl-job/          # Job Scheduling (定时任务调度中心)
```

## 4. Architecture & Design Patterns (架构与设计模式)

### 4.1 Market Replay (行情回放)
- **Location**: `services/quant-data-collector/src/main/java/com/hao/datacollector/replay/`
- **Mechanism**:
  - `DataLoader`: Preloads history data from MySQL (cold/hot partitioned).
  - `TimeSliceBuffer`: Buffers data by seconds.
  - `ReplayScheduler`: Controls virtual time and pushes to Kafka topic `quotation`.

### 4.2 Strategy Engine (策略引擎)
- **Location**: `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/strategy/`
- **Pattern**: Strategy Pattern + Composite Pattern.
- **Interface**: `QuantStrategy` (input: `StrategyContext`, output: `StrategyResult`).
- **Types**:
  - **Signal**: Generates buy/sell signals (e.g., `MomentumStrategy`).
  - **Information**: Provides context (e.g., `HotTopicStrategy`).
  - **Composite**: Combines multiple strategies.

### 4.3 High-Concurrency Stock List (高并发选股表)
- **Location**: `services/quant-stock-list/`
- **Architecture**: CQRS (Write: Strategy Engine -> Kafka; Read: Stock List Service).
- **Caching**: Multi-level (Caffeine L1 + Redis L2).
- **Protection**:
  - **Bloom Filter**: Intercept invalid queries.
  - **Distributed Lock (Redisson)**: Prevent cache breakdown.
  - **Logical Expiry**: Return stale data while refreshing asynchronously.

### 4.4 Distributed Logging (分布式日志)
- **Implementation**: Logback + Kafka Appender.
- **Flow**: Service Logs -> Kafka (JSON format) -> `quant-data-archive` -> Elasticsearch (Planned).
- **Config**: Nacos dynamically updates logging config (`KafkaLogbackConfig`).
- **Standard**: All topics defined in `KafkaConstants`.

## 5. Build & Run Commands (构建与运行命令)

### Build (构建)
```bash
# Build entire project (构建整个项目)
mvn clean install

# Build specific module (构建特定模块)
mvn clean install -pl services/quant-data-collector -am

# Skip tests (跳过测试 - Recommended for quick builds)
mvn clean install -DskipTests
```

### Run Services (运行服务)
```bash
# Run a specific service (运行服务)
mvn spring-boot:run -pl services/quant-data-collector

# Run with profiles (指定环境)
mvn spring-boot:run -pl services/quant-data-collector -Dspring-boot.run.profiles=dev
```

### Test (测试)
```bash
# Run all tests (运行所有测试)
mvn test

# Run tests for a module (模块测试)
mvn test -pl services/quant-strategy-engine

# Run single test class (单类测试)
mvn test -Dtest=TestClassName
```

## 6. Development Guidelines (开发指南)

### 6.1 Coding Standards (代码规范)
- **Annotations**: Use Lombok (`@Data`, `@Slf4j`, `@Builder`) extensively.
- **Dates**: Use Java 21 `LocalDateTime`. Use `DateUtil` for formatting.
- **Exceptions**: Throw `BusinessException` for logic errors.
- **Constants**: NO magic numbers/strings. Define in `common/.../constants/`.
- **Response**: All controllers return a unified wrapper (e.g., `Result<T>`).

### 6.2 Adding a New Strategy (新增策略)
1. Create class implementing `QuantStrategy` in `services/quant-strategy-engine`.
2. Annotate with `@Component`.
3. Implement `execute(StrategyContext context)`.
4. Register unique ID in `StrategyMetaEnum`.

### 6.3 Adding a New Log Topic (新增日志Topic)
1. Add constant in `KafkaConstants` (e.g., `TOPIC_LOG_NEW_SERVICE`).
2. Add topic to `KafkaLogbackConfig` or `logback.xml` via Nacos property.
3. Update `LogConsumerService` in `quant-data-archive` to listen to the new topic.

### 6.4 Common File Paths (常用文件路径)
- **Constants**: `common/src/main/java/constants/`
- **Exceptions**: `common/src/main/java/exception/BusinessException.java`
- **Kafka Topics**: `common/src/main/java/integration/kafka/KafkaConstants.java`
- **Strategy Interface**: `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/strategy/QuantStrategy.java`
- **Replay Config**: `services/quant-data-collector/src/main/resources/application.yml`

## 7. Troubleshooting (常见问题)
- **Compilation Errors**: Usually due to Lombok processing or missing `common` dependency. Run `mvn clean install -DskipTests` on root first.
- **Kafka Connection**: Check `application.yml` or Nacos config for correct bootstrap servers.
- **Missing Logs**: Ensure `KafkaLogbackConfig` is initialized and `KafkaAppender` is started.
