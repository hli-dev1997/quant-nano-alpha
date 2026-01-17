# Quant Strategy Engine 深度解析文档

本文档详细解析 `quant-strategy-engine` 模块的代码结构、分层逻辑以及核心数据流转过程。

## 1. 模块结构与分层 (Structure & Layering)

本模块采用极简的流式计算架构，专注于高吞吐量的实时行情处理与策略计算。

### 1.1 目录结构说明

```text
com.hao.strategyengine
├── config/                  # [配置层] 全局配置
│   ├── KafkaLogbackConfig   # 日志配置（对接 Kafka）
│   └── StreamComputeProperties # 流计算核心参数（线程数、RingBuffer大小等）
│
├── core/                    # [核心层] 业务逻辑核心
│   ├── stream/
│   │   ├── domain/          # [领域模型] 核心数据结构
│   │   │   ├── Tick         # 行情快照（不可变对象）
│   │   │   └── StockDomainContext # 单个股票的内存状态（RingBuffer存储历史）
│   │   ├── engine/          # [引擎层] 调度与计算
│   │   │   ├── StreamDispatchEngine # 调度引擎（数据入口，负责解析与路由）
│   │   │   └── StreamComputeEngine  # 计算引擎（基于线程封闭模型执行策略）
│   │   └── strategy/        # [策略层] 量化策略实现
│   │       ├── StreamingStrategy    # 策略接口
│   │       └── impl/NineTurnStrategy # 九转序列策略实现
│
├── integration/             # [基础设施层] 外部依赖交互
│   ├── kafka/               # Kafka 消息收发
│   │   ├── KafkaConsumerConfig  # 消费者配置
│   │   └── KafkaConsumerService # 消息监听器
│   └── redis/               # Redis 存储
│       ├── RedisClient      # Redis 客户端封装
│       └── RedisStrategyRepository # 策略结果仓储
│
└── StrategyEngineApplication # 启动类
```

### 1.2 分层职责

1.  **Infrastructure Layer (Integration)**: 负责与 Kafka 和 Redis 的物理连接，不包含复杂业务逻辑。
2.  **Engine Layer (Core/Engine)**: 负责数据的路由分发和并发控制。
    *   `StreamDispatchEngine`: 充当 "海关"，负责清洗数据（DTO转Domain）和初步路由。
    *   `StreamComputeEngine`: 充当 "车间"，利用 Hash 算法将同一股票分配到固定线程，实现无锁计算。
3.  **Domain Layer (Core/Domain)**: 定义核心业务对象。
    *   `StockDomainContext` 是核心的状态容器，维护每只股票的历史走势。
4.  **Strategy Layer (Core/Strategy)**: 纯粹的量化逻辑实现，只关心数学计算，不关心数据来源和存储。

---

## 2. 完整数据流程详解 (Complete Data Flow)

以下是从 Kafka 接收行情数据到策略结果存入 Redis 的完整步骤。

### 起点：Kafka 消息接入

**场景**：上游 `quant-data-collector` 采集到行情数据，发送到 Kafka `quotation` Topic。

#### 步骤 1: 消费 Kafka 消息
*   **类/方法**: `KafkaConsumerService.onMessage(List<String> messages)`
*   **作用**: 作为 Kafka 的监听器，批量接收 JSON 格式的行情数据。
*   **逻辑**:
    *   使用 `@KafkaListener` 监听 `quotation` 主题。
    *   接收 `List<String>` 批量消息以提高吞吐量。
    *   **分支**: 如果列表为空，直接返回。
    *   **下一步**: 调用 `streamDispatchEngine.dispatch(message)`。

#### 步骤 2: 数据清洗与解析
*   **类/方法**: `StreamDispatchEngine.dispatch(String message)`
*   **作用**: 防腐层（ACL），将外部 JSON 数据转换为内部 `Tick` 领域对象。
*   **逻辑**:
    *   使用 Jackson 解析 JSON。
    *   **构建对象**: 使用 Builder 模式构建 `Tick` 对象（包含 symbol, price, volume, time 等）。
    *   **下一步**: 调用 `streamComputeEngine.process(tick)`。

#### 步骤 3: 线程路由 (Thread Routing)
*   **类/方法**: `StreamComputeEngine.process(Tick tick)`
*   **作用**: 实现 "线程封闭" (Thread Confinement) 策略，确保同一只股票永远由同一个线程处理，避免多线程竞争。
*   **逻辑**:
    *   **Hash 算法**: `int slot = Math.abs(tick.getSymbol().hashCode()) % workerCount`。
    *   **任务提交**: 将处理任务包装为 Runnable 提交到 `workers[slot]` 对应的单线程 ExecutorService。
    *   **下一步**: 异步执行 `doProcess(tick)` 方法。

### 核心：策略计算

#### 步骤 4: 获取领域上下文
*   **类/方法**: `StreamComputeEngine.doProcess(Tick tick)`
*   **作用**: 准备计算所需的上下文环境。
*   **逻辑**:
    *   **查找 Context**: 从 `contextMap` (ConcurrentHashMap) 中根据 symbol 获取 `StockDomainContext`。
    *   **分支**: 如果是该股票的第一条数据，创建一个新的 `StockDomainContext`。
    *   **更新状态**: 调用 `context.addTick(tick)` 将最新价格存入 RingBuffer，更新历史数据。

#### 步骤 5: 执行策略链
*   **类/方法**: `StreamComputeEngine.doProcess` (续) -> `StreamingStrategy.isMatch`
*   **作用**: 遍历所有注册的策略，判断是否触发信号。
*   **逻辑**:
    *   遍历 `strategies` 列表 (例如 `NineTurnStrategy`)。
    *   调用 `strategy.isMatch(tick, context)`。

#### 步骤 6: 策略逻辑判断 (以九转序列为例)
*   **类/方法**: `NineTurnStrategy.isMatch(Tick tick, StockDomainContext context)`
*   **作用**: 执行具体的量化逻辑。
*   **逻辑**:
    *   **数据回溯**: 通过 `context.getPrice(4)` 获取 4 天前的收盘价 (O(1)复杂度)。
    *   **条件判断**: 比较当前价格与 4 天前价格。
    *   **状态机**:
        *   如果 `Close > Close_T-4`: 计数器 +1 (牛市 Setup)。
        *   如果不满足: 计数器重置为 0。
    *   **分支**: 如果计数器达到 9，返回 `true` (触发信号)。

### 终点：结果持久化

#### 步骤 7: 处理策略结果
*   **类/方法**: `StreamComputeEngine.doProcess` (续)
*   **作用**: 收集策略返回的信号。
*   **逻辑**:
    *   如果 `isMatch` 返回 `true`，记录策略 ID (例如 "NINE_TURN")。
    *   **下一步**: 调用 `repository.saveMatch(strategyId, symbol)`。

#### 步骤 8: 存入 Redis
*   **类/方法**: `RedisStrategyRepository.saveMatch(String strategyId, String symbol)`
*   **作用**: 将选股结果写入 Redis，供下游服务查询。
*   **逻辑**:
    *   **生成 Key**: `STRATEGY:{StrategyId}:{yyyyMMdd}` (例如 `STRATEGY:NINE_TURN:20260102`)。
    *   **写入 Set**: `redisTemplate.opsForSet().add(key, symbol)`。
    *   **特性**: 使用 Set 结构自动去重。

---

## 3. 总结

整个流程是一个高度优化的 **SEDA (Staged Event-Driven Architecture)** 变体：

1.  **I/O 阶段**: Kafka 消费者批量拉取 (多线程)。
2.  **分发阶段**: `DispatchEngine` 快速解析并 Hash 路由。
3.  **计算阶段**: `ComputeEngine` 在内存中完成无锁计算 (纯内存操作，极快)。
4.  **存储阶段**: 结果异步写入 Redis。

这种设计确保了在处理全市场 5000+ 只股票的高频 tick 数据时，系统吞吐量不受锁竞争的影响。
