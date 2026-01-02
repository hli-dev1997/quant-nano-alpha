# Quant Strategy Engine (极简版)

> **Bare Metal Architecture** - 只保留流式计算核心链路

---

## 架构概览

```
Kafka → StreamDispatchEngine → StreamComputeEngine → NineTurnStrategy → Redis
```

**14 个核心文件，0 冗余代码。**

---

## 目录结构

```
src/main/java/com/hao/strategyengine/
├── StrategyEngineApplication.java     # 启动类
├── config/
│   ├── KafkaLogbackConfig.java        # 日志配置
│   └── StreamComputeProperties.java   # 流式计算配置
├── core/stream/
│   ├── domain/
│   │   ├── Tick.java                  # 行情数据（Immutable）
│   │   └── StockDomainContext.java    # 内存状态（RingBuffer）
│   ├── engine/
│   │   ├── StreamComputeEngine.java   # 计算引擎（Thread Confinement）
│   │   └── StreamDispatchEngine.java  # Kafka 入口
│   └── strategy/
│       ├── StreamingStrategy.java     # 策略接口
│       └── impl/NineTurnStrategy.java # 九转策略
└── integration/
    ├── kafka/
    │   ├── KafkaConsumerConfig.java   # Kafka 配置
    │   └── KafkaConsumerService.java  # 消息消费
    └── redis/
        ├── RedisClient.java           # Redis 接口
        ├── RedisConfig.java           # Redis 配置
        └── RedisStrategyRepository.java # 结果存储
```

---

## 核心设计

### 1. Thread Confinement（无锁并发）

```java
int slot = Math.abs(tick.symbol.hashCode()) % workerCount;
workers[slot].submit(() -> doProcess(tick, slot));
```

**同一股票永远路由到同一 Worker，无需加锁。**

### 2. RingBuffer（O(1) 历史访问）

```java
public double getPrice(int daysAgo) {
    int index = (cursor - daysAgo + capacity) % capacity;
    return prices[index];
}
```

**固定容量，自动覆盖旧数据，内存友好。**

### 3. 黑板模式（Redis Set）

```java
redisTemplate.opsForSet().add("STRATEGY:NINE_TURN:20260102", symbol);
```

**生产者只写，消费者随时读，完全解耦。**

---

## 配置

```yaml
stream:
  compute:
    enabled: true
    worker-threads: 8     # CPU 核数
    history-size: 250     # RingBuffer 容量
```

---

## 测试

```bash
mvn test
```

**4 个测试类，全部通过。**

---

## 删除清单（本次重构）

- ❌ `api/controller` - HTTP 入口
- ❌ `chain` - 责任链
- ❌ `monitoring` - 监控告警
- ❌ `resilience` - 熔断限流
- ❌ `strategy` (旧) - 10 个旧策略
- ❌ `service` - 旧业务层
- ❌ `common` - 旧工具类
- ❌ `core/facade|dispatcher|registry` - 旧调度层

**从 62 个文件精简到 14 个。**

---

*极简即是美。*
