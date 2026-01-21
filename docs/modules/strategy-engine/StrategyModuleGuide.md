# 🧠 Quant Strategy 模块说明文档

> 模块路径：`com.hao.strategyengine.strategy`  
>  
> 本模块定义并实现系统中所有的 **量化策略（QuantStrategy）**，  
> 是整个量化选股与回测引擎的核心执行层。

---

## 🧩 一、什么是“策略”（QuantStrategy）

在量化系统中，**策略（Strategy）** 是一段可独立执行的算法逻辑，  
它基于输入的市场数据或上下文信息（`StrategyContext`），  
输出对应的决策结果（`StrategyResult`）。

### 策略接口定义
```java
public interface QuantStrategy {
    String getId();                       // 策略唯一标识
    StrategyResult execute(StrategyContext context);  // 执行策略逻辑
}
````

### 策略的三个核心特征

| 特征        | 说明                                                         |
| --------- | ---------------------------------------------------------- |
| **输入标准化** | 所有策略均接收 `StrategyContext` 作为输入，包含 userId、symbol、extra 参数等。 |
| **输出统一化** | 所有策略返回 `StrategyResult`，包含策略ID、结果数据、耗时等。                   |
| **可组合性**  | 多个策略可通过 `CompositeStrategy` 组合形成复合决策逻辑。                    |

---

## 🧠 二、策略的分类

根据逻辑性质与职责，策略分为三大类：

| 类型                                | 定义                      | 示例                                         | 作用            |
| --------------------------------- | ----------------------- | ------------------------------------------ | ------------- |
| **1️⃣ 信号型（Signal Strategy）**      | 直接基于行情或指标计算交易信号（买/卖/持有） | `MovingAverageStrategy`、`MomentumStrategy` | 生成可执行的交易信号    |
| **2️⃣ 信息型（Information Strategy）** | 提供辅助信息或过滤条件，不直接输出买卖信号   | `HotTopicStrategy`（热点题材库）                  | 构建选股池、提供上下文信息 |
| **3️⃣ 复合型（Composite Strategy）**   | 组合多个子策略形成更复杂的逻辑决策       | `CompositeStrategy`                        | 策略共振、多因子综合判断  |

### 🔹 策略分类结构图

```
QuantStrategy
 ├── Signal Strategy
 │     ├── MomentumStrategy      → 动量策略
 │     └── MovingAverageStrategy → 均线策略
 │
 ├── Information Strategy
 │     └── HotTopicStrategy      → 热点题材库策略
 │
 └── Composite Strategy
       └── CompositeStrategy     → 复合策略（多策略组合）
```

---

## ⚙️ 三、策略生命周期（执行流程）

```text
Controller → Facade → Dispatcher → QuantStrategy
                    ↳ (CachingDecorator 缓存包装)
                     ↳ (Redis 数据源读取)
                      ↳ (计算/过滤逻辑)
                       ↳ 返回 StrategyResult
```

1. **Controller**：接收用户请求，构建 `StrategyContext`。
2. **Facade**：执行责任链校验 + 分布式锁（Redisson）。
3. **Dispatcher**：分发到对应策略实例，可添加装饰器增强（缓存、日志、限流等）。
4. **Strategy 执行**：

    * 从 Redis/MySQL 获取数据源
    * 执行策略算法或过滤逻辑
    * 封装为 `StrategyResult`
5. **KafkaResultPublisher**：异步推送执行结果到 Kafka 以供后续落库、监控。

---

## 🔍 四、信息型策略（Information Strategy）说明

信息型策略并不直接计算买卖信号，
而是为其他策略提供过滤维度或数据上下文，常用于复合策略场景。

### 示例：HotTopicStrategy

* **功能**：从 Redis 中查询热点题材与关联股票列表
* **用途**：构建“热点股票池”
* **输出**：符合题材条件的股票集合
* **组合使用示例**：

  ```java
  CompositeStrategy combo = new CompositeStrategy(
      new HotTopicStrategy(redisClient),
      new MomentumStrategy(),
      new MovingAverageStrategy()
  );
  ```

  执行逻辑：

  > 热点股票池 → 动量信号 → 均线共振 → 输出候选股票

---

## 🧱 五、策略扩展规范

| 扩展点        | 说明                                          |
| ---------- | ------------------------------------------- |
| **策略注册**   | 新增策略类需实现 `QuantStrategy` 接口并标注 `@Component` |
| **策略缓存**   | 若策略结果可复用，使用 `CachingDecorator` 包装           |
| **策略组合**   | 可通过 `CompositeStrategy` 将多个策略以并行/顺序方式组合     |
| **策略数据源**  | Redis 用于热点数据或指标缓存，MySQL 用于原始历史数据            |
| **策略指标监控** | 每个策略执行耗时与命中率可通过 `MetricsCollector` 采集       |

---

## 🔔 六、设计思想

策略模块遵循以下设计原则：

1. **单一职责原则（SRP）**
   每个策略只负责一类逻辑计算，避免冗杂判断。

2. **可插拔性（Plug-in Architecture）**
   策略间相互独立，可灵活组合与替换。

3. **可扩展性（Extensibility）**
   支持未来扩展更多数据维度（情绪、新闻、板块、异动等）。

4. **高可观测性（Observability）**
   每个策略的执行耗时、缓存命中率、异常情况均可被监控。

---

## 📘 七、总结

| 策略类型           | 定位     | 组合潜力   | 示例                |
| -------------- | ------ | ------ | ----------------- |
| Signal 策略      | 输出买卖信号 | ✅ 可组合  | Momentum / MA     |
| Information 策略 | 提供辅助数据 | ✅ 可组合  | HotTopic          |
| Composite 策略   | 聚合多个策略 | ✅ 组合核心 | CompositeStrategy |

> **一句话总结：**
> 「策略模块」是系统的 **决策大脑**，
> 每个策略都是一个可独立演化的“神经元”，
> 通过组合与异步并行，最终形成完整的选股决策网络。

---

**作者：** hli
**最后修改日期：** 2025-10-26
