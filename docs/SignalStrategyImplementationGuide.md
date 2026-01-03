# 📘 信号型策略实现完整流程指南

> **模块路径**: `com.hao.strategyengine`  
> **最后更新**: 2026-01-01  
> **作者**: hli

本文档详细描述如何在量化策略引擎中实现一个**信号型策略（Signal Strategy）**的完整流程，从 HTTP 请求到策略执行再到结果发布，涵盖每个类的每个关键方法。

---

## 📊 一、系统调用链全景图

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                         信号型策略执行完整调用链                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [第1层] StrategyController.execute()                                       │
│              │                                                              │
│              │ ① 接收HTTP请求 → 构建 StrategyContext                        │
│              ▼                                                              │
│  [第2层] StrategyEngineFacade.executeAll()                                  │
│              │                                                              │
│              │ ② 风控责任链校验 → 分布式锁控制 → 并行调度                    │
│              ▼                                                              │
│  [第3层] StrategyChain.apply()          ←── 前置责任链(风控/验证/限流)       │
│              │                                                              │
│              ▼                                                              │
│  [第4层] DistributedLockService.acquireOrWait()  ←── 分布式锁防重复计算     │
│              │                                                              │
│              ▼                                                              │
│  [第5层] StrategyDispatcher.dispatch()                                      │
│              │                                                              │
│              │ ③ 从注册表获取策略 → 装饰器包装 → 执行策略                    │
│              ▼                                                              │
│  [第6层] CachingDecorator.execute()     ←── 装饰器模式(缓存增强)            │
│              │                                                              │
│              ▼                                                              │
│  [第7层] MomentumStrategy.execute()     ←── 核心策略逻辑                    │
│              │                                                              │
│              │ ④ 获取数据 → 计算指标 → 生成信号 → 返回结果                  │
│              ▼                                                              │
│  [输出层] StrategyResult → StrategyResultBundle                             │
│              │                                                              │
│              │ ⑤ 缓存结果 → Kafka发布 → SSE推送                             │
│              ▼                                                              │
│  [消费层] quant-stock-list 服务 → 落库持久化                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔷 二、核心类与方法详解

### 2.1 StrategyController（第1层：请求入口）

**文件路径**: `api/controller/StrategyController.java`

**类职责**: 提供 HTTP 接口，接收策略执行请求，构建上下文并通过 SSE 流式返回结果。

#### 核心方法

| 方法签名 | 功能说明 |
|---------|---------|
| `execute(StrategyRequest req)` | 主入口，创建 SSE 通道，异步执行策略并推送结果 |
| `execute1(StrategyRequest req)` | 备用入口，异步触发无同步返回（用于调试） |

#### execute 方法执行流程

```java
@PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter execute(@RequestBody StrategyRequest req) {
    // Step 1: 创建 SSE 流，设置 30 秒超时
    SseEmitter emitter = new SseEmitter(30_000L);
    
    // Step 2: 异步执行，避免阻塞 Controller 线程
    CompletableFuture.runAsync(() -> {
        try {
            // Step 3: 构建策略上下文对象
            StrategyContext ctx = StrategyContext.builder()
                    .userId(req.getUserId())
                    .symbol(req.getSymbol())
                    .extra(req.getExtra())
                    .requestTime(Instant.now())
                    .build();
            
            // Step 4: 调用 Facade 执行策略组合
            StrategyResultBundle bundle = engine.executeAll(
                req.getUserId(), req.getStrategyIds(), ctx);
            
            // Step 5: 异步发布 Kafka 消息
            kafkaPublisher.publish("quant-strategy-result", bundle);
            
            // Step 6: SSE 推送结果给前端
            emitter.send(bundle);
            
            // Step 7: 完成推送并关闭连接
            emitter.complete();
            
        } catch (Exception e) {
            // Step 8: 异常处理，推送错误事件
            emitter.send(SseEmitter.event().name("error").data("执行异常：" + e.getMessage()));
            emitter.completeWithError(e);
        }
    });
    return emitter;
}
```

---

### 2.2 StrategyEngineFacade（第2层：外观层）

**文件路径**: `core/facade/StrategyEngineFacade.java`

**类职责**: 策略引擎的统一入口（Facade 模式），封装风控链、分布式锁、并行调度、缓存与消息发布。

#### 依赖注入

| 字段 | 类型 | 说明 |
|-----|------|-----|
| `dispatcher` | StrategyDispatcher | 策略分发器 |
| `chain` | StrategyChain | 风控责任链 |
| `lockService` | DistributedLockService | 分布式锁服务 |
| `cacheService` | StrategyCacheService | 结果缓存服务 |
| `kafkaPublisher` | KafkaResultPublisher | Kafka 发布器 |
| `pool` | ExecutorService | 自定义线程池(8-64线程) |

#### executeAll 方法详解

```java
public StrategyResultBundle executeAll(Integer userId, List<String> strategyIds, 
                                        StrategyContext ctx) throws Exception {
    // Step 1: 前置责任链风控校验
    chain.apply(ctx);
    
    // Step 2: 生成组合Key（如 "MA_MOM_DRAGON_TWO"）
    String comboKey = KeyUtils.comboKey(strategyIds);
    
    // Step 3: 构建计算逻辑 Supplier（惰性执行）
    Supplier<StrategyResultBundle> compute = () -> {
        // (1) 异步并行执行每个策略
        List<CompletableFuture<StrategyResult>> futures = strategyIds.stream()
                .map(id -> CompletableFuture.supplyAsync(
                    () -> dispatcher.dispatch(id, ctx), pool))
                .collect(Collectors.toList());
        
        // (2) 阻塞等待全部策略执行完成
        List<StrategyResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
        
        // (3) 封装为聚合结果包
        StrategyResultBundle bundle = new StrategyResultBundle(comboKey, results);
        
        // (4) 异步缓存与消息发布
        cacheService.save(bundle);
        kafkaPublisher.publish("quant-strategy-result", bundle);
        return bundle;
    };
    
    // Step 4: 分布式锁控制，确保同一组合只执行一次
    return lockService.acquireOrWait(comboKey, compute);
}
```

---

### 2.3 StrategyChain（第3层：责任链）

**文件路径**: `chain/StrategyChain.java`

**类职责**: 策略前置责任链，按顺序执行多个处理器（风控/验证/限流）。

#### 核心方法

```java
@Component
@RequiredArgsConstructor
public class StrategyChain {
    
    // Spring 自动注入所有 StrategyHandler 实现
    private final List<StrategyHandler> handlers;
    
    /**
     * 执行责任链中的所有前置处理器
     * @param ctx 策略上下文
     * @throws Exception 任一处理器异常则中断执行
     */
    public void apply(StrategyContext ctx) throws Exception {
        for (StrategyHandler handler : handlers) {
            handler.handle(ctx);  // 依次执行每个处理器
        }
        // 所有 Handler 校验通过，放行策略计算阶段
    }
}
```

#### StrategyHandler 接口

```java
public interface StrategyHandler {
    /**
     * 处理策略上下文
     * @param ctx 策略执行上下文
     * @throws Exception 可抛出异常以中断策略执行链
     */
    void handle(StrategyContext ctx) throws Exception;
}
```

---

### 2.4 StrategyDispatcher（第5层：分发器）

**文件路径**: `core/dispatcher/StrategyDispatcher.java`

**类职责**: 策略分发器，根据策略ID获取策略实例，通过装饰器增强后执行。

#### dispatch 方法

```java
@Component
@RequiredArgsConstructor
public class StrategyDispatcher {
    
    private final StrategyRegistry registry;      // 策略注册表
    private final StrategyCacheService cacheService;  // 缓存服务
    
    /**
     * 根据策略ID分发并执行策略
     * @param strategyId 策略ID
     * @param ctx 策略执行上下文
     * @return 策略执行结果
     */
    public StrategyResult dispatch(String strategyId, StrategyContext ctx) {
        // Step 1: 从注册表获取策略实例
        QuantStrategy s = registry.get(strategyId);
        if (s == null) {
            throw new IllegalArgumentException("unknown strategy: " + strategyId);
        }
        
        // Step 2: 使用缓存装饰器包装策略
        QuantStrategy wrapped = new CachingDecorator(s, cacheService);
        
        // Step 3: 执行被装饰后的策略
        return wrapped.execute(ctx);
    }
}
```

---

### 2.5 StrategyRegistry（策略注册表）

**文件路径**: `core/registry/StrategyRegistry.java`

**类职责**: 管理系统中所有可用的策略实例，提供按ID快速获取功能。

```java
@Component
public class StrategyRegistry {
    
    private final Map<String, QuantStrategy> strategyMap;
    
    /**
     * 构造方法：Spring 自动注入所有 QuantStrategy Bean
     */
    @Autowired
    public StrategyRegistry(List<QuantStrategy> strategyBeans) {
        this.strategyMap = strategyBeans.stream()
                .collect(Collectors.toMap(QuantStrategy::getId, Function.identity()));
    }
    
    /** 根据策略ID获取策略实例 */
    public QuantStrategy get(String id) {
        return strategyMap.get(id);
    }
    
    /** 获取所有策略ID */
    public Set<String> ids() {
        return Collections.unmodifiableSet(strategyMap.keySet());
    }
}
```

---

### 2.6 CachingDecorator（第6层：装饰器）

**文件路径**: `strategy/decorator/CachingDecorator.java`

**类职责**: 策略装饰器（Decorator模式），为策略添加缓存功能。

```java
public class CachingDecorator implements QuantStrategy {
    
    private final QuantStrategy delegate;     // 被装饰的策略
    private final StrategyCacheService cacheService;
    
    public CachingDecorator(QuantStrategy delegate, StrategyCacheService cacheService) {
        this.delegate = delegate;
        this.cacheService = cacheService;
    }
    
    @Override
    public String getId() {
        return delegate.getId();
    }
    
    @Override
    public StrategyResult execute(StrategyContext context) {
        // 缓存 key = 策略ID + 标的symbol
        String key = delegate.getId() + ":" + context.getSymbol();
        // 获取缓存或计算
        return cacheService.getOrCompute(key, () -> delegate.execute(context));
    }
}
```

---

### 2.7 QuantStrategy 接口（策略契约）

**文件路径**: `strategy/QuantStrategy.java`

**类职责**: 策略模式核心接口，定义所有策略的统一契约。

```java
public interface QuantStrategy {
    
    /**
     * 获取策略唯一标识
     * @return 策略ID（如 "SIG_MOMENTUM"）
     */
    String getId();
    
    /**
     * 执行策略逻辑
     * @param context 策略上下文
     * @return 策略执行结果
     */
    StrategyResult execute(StrategyContext context);
}
```

---

### 2.8 MomentumStrategy（第7层：具体策略实现）

**文件路径**: `strategy/impl/signal/MomentumStrategy.java`

**类职责**: 动量策略实现，基于价格动量生成买卖信号。

#### 核心常量

| 常量名 | 值 | 说明 |
|-------|-----|-----|
| `SHORT_TERM_PERIOD` | 5 | 短期周期（5日） |
| `MID_TERM_PERIOD` | 10 | 中期周期（10日） |
| `LONG_TERM_PERIOD` | 20 | 长期周期（20日） |
| `VOLUME_RATIO_THRESHOLD` | 1.1 | 成交量比率阈值 |
| `MAX_RESULTS` | 40 | 最大返回结果数 |

#### execute 方法（核心算法）

```java
@Override
public StrategyResult execute(StrategyContext context) {
    long start = System.currentTimeMillis();
    
    try {
        // Step 1: 获取股票池
        List<String> stockPool = getStockPool();
        List<Map<String, Object>> selectedStocks = new ArrayList<>();
        
        for (String stockCode : stockPool) {
            // Step 2: 获取历史价格和成交量数据
            List<Double> prices = getHistoricalPrices(stockCode);
            List<Double> volumes = getHistoricalVolumes(stockCode);
            
            // 数据量不足，跳过
            if (prices.size() < MIN_DATA_SIZE) continue;
            
            // Step 3: 计算不同周期收益率
            double return5D = calculateReturn(prices, SHORT_TERM_PERIOD);
            double return10D = calculateReturn(prices, MID_TERM_PERIOD);
            double return20D = calculateReturn(prices, LONG_TERM_PERIOD);
            
            // Step 4: 计算成交量比率
            double volumeRatio = calculateVolumeRatio(volumes, 
                SHORT_TERM_PERIOD, LONG_TERM_PERIOD);
            
            // Step 5: 动量条件判断
            boolean momentumCondition = return5D > return10D 
                && return10D > return20D && return20D > 0;
            boolean volumeCondition = volumeRatio > VOLUME_RATIO_THRESHOLD;
            
            // Step 6: 满足条件则计算综合得分
            if (momentumCondition && volumeCondition) {
                double score = calculateMomentumScore(
                    return5D, return10D, return20D, volumeRatio);
                
                Map<String, Object> stockSignal = new HashMap<>();
                stockSignal.put("wind_code", stockCode);
                stockSignal.put("signal_score", score);
                stockSignal.put("current_price", prices.get(prices.size() - 1));
                stockSignal.put("return_5d", return5D * 100);
                stockSignal.put("return_10d", return10D * 100);
                stockSignal.put("return_20d", return20D * 100);
                stockSignal.put("volume_ratio", volumeRatio);
                selectedStocks.add(stockSignal);
            }
        }
        
        // Step 7: 按动量分数降序排列并截取
        selectedStocks.sort((a, b) -> 
            Double.compare((Double)b.get("signal_score"), 
                          (Double)a.get("signal_score")));
        if (selectedStocks.size() > MAX_RESULTS) {
            selectedStocks = selectedStocks.subList(0, MAX_RESULTS);
        }
        
        // Step 8: 构建返回结果
        return StrategyResult.builder()
                .strategyId(getId())
                .data(selectedStocks)
                .durationMs(System.currentTimeMillis() - start)
                .build();
                
    } catch (Exception e) {
        return buildErrorResult(start, e.getMessage());
    }
}
```

#### 辅助方法

| 方法名 | 参数 | 返回值 | 功能说明 |
|-------|------|-------|---------|
| `getId()` | - | String | 返回策略ID（`SIG_MOMENTUM`） |
| `getStockPool()` | - | List\<String\> | 获取股票池 |
| `getHistoricalPrices(stockCode)` | String | List\<Double\> | 获取历史价格 |
| `getHistoricalVolumes(stockCode)` | String | List\<Double\> | 获取历史成交量 |
| `calculateReturn(prices, days)` | List\<Double\>, int | double | 计算指定周期收益率 |
| `calculateVolumeRatio(volumes, recent, historical)` | List\<Double\>, int, int | double | 计算成交量比率 |
| `calculateMomentumScore(r5, r10, r20, volRatio)` | double×4 | double | 计算综合动量得分 |
| `buildErrorResult(start, errorMsg)` | long, String | StrategyResult | 构建错误结果 |

---

## 🔷 三、数据模型

### 3.1 StrategyContext（策略上下文）

**文件路径**: `common/model/core/StrategyContext.java`

```java
@Data
@Builder
public class StrategyContext {
    private Integer userId;          // 用户唯一标识
    private String symbol;           // 交易标的代码
    private Map<String, Object> extra;  // 扩展字段
    private Instant requestTime;     // 请求时间
}
```

### 3.2 StrategyResult（策略结果）

**文件路径**: `common/model/response/StrategyResult.java`

```java
@Data
@Builder
public class StrategyResult {
    private String strategyId;       // 策略ID
    private Object data;             // 策略返回数据
    private long durationMs;         // 执行耗时（毫秒）
    @Builder.Default
    private boolean isSuccess = true; // 是否执行成功
}
```

### 3.3 StrategyResultBundle（聚合结果包）

```java
public class StrategyResultBundle {
    private String comboKey;                 // 策略组合Key
    private List<StrategyResult> results;    // 各策略结果列表
}
```

---

## 🔷 四、新增信号型策略步骤

### Step 1: 创建策略类

在 `strategy/impl/signal/` 目录下创建新策略类：

```java
@Slf4j
@Component
public class MyNewSignalStrategy implements QuantStrategy {
    
    @Override
    public String getId() {
        return StrategyMetaEnum.SIG_MY_NEW.getId();
    }
    
    @Override
    public StrategyResult execute(StrategyContext context) {
        long start = System.currentTimeMillis();
        
        try {
            // 1. 获取数据
            // 2. 计算指标
            // 3. 生成信号
            // 4. 构建结果
            
            return StrategyResult.builder()
                    .strategyId(getId())
                    .data(resultData)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
                    
        } catch (Exception e) {
            log.error("策略执行失败|Strategy_execution_failed", e);
            return buildErrorResult(start, e.getMessage());
        }
    }
}
```

### Step 2: 注册策略元数据

在 `StrategyMetaEnum` 枚举中添加新策略：

```java
public enum StrategyMetaEnum {
    SIG_MY_NEW("SIG_MY_NEW", "我的新信号策略", StrategyType.SIGNAL),
    // ...其他策略
}
```

### Step 3: 添加测试

创建单元测试验证策略逻辑：

```java
@SpringBootTest
class MyNewSignalStrategyTest {
    
    @Autowired
    private MyNewSignalStrategy strategy;
    
    @Test
    void testExecute() {
        StrategyContext ctx = StrategyContext.builder()
                .userId(1)
                .symbol("000001.SZ")
                .build();
                
        StrategyResult result = strategy.execute(ctx);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("SIG_MY_NEW", result.getStrategyId());
    }
}
```

---

## 🔷 五、设计模式总结

| 模式 | 应用位置 | 说明 |
|-----|---------|-----|
| **策略模式** | QuantStrategy 接口 | 封装不同算法为独立类，可互换 |
| **外观模式** | StrategyEngineFacade | 统一入口，屏蔽内部复杂性 |
| **责任链模式** | StrategyChain | 前置处理器链，可插拔校验逻辑 |
| **装饰器模式** | CachingDecorator | 非侵入式增强策略功能 |
| **注册模式** | StrategyRegistry | 集中管理策略实例，按ID索引 |
| **建造者模式** | StrategyContext/Result | Lombok @Builder 简化对象构造 |

---

## 🔷 六、性能优化要点

1. **并行执行**: 多策略通过 `CompletableFuture` 并行计算
2. **线程池隔离**: 自定义线程池(8-64)，CallerRunsPolicy 拒绝策略
3. **分布式锁**: 防止集群重复计算同一策略组合
4. **结果缓存**: CachingDecorator 减少重复计算
5. **异步发布**: Kafka 消息异步推送，不阻塞主流程
6. **SSE 流式**: 避免长连接阻塞，支持实时响应

---

**文档作者**: hli  
**最后更新**: 2026-01-01
