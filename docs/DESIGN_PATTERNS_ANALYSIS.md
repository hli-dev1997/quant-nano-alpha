# quant-nano-alpha 设计模式分析报告

> **分析目标**：扫描整个项目，识别可应用 GoF 23种设计模式的场景，并提供具体实现建议。

---

## 📊 分析概览

| 类别 | 已存在模式 | 可优化/新增模式 |
|------|-----------|----------------|
| 创建型 (Creational) | Singleton (Spring Bean) | Factory, Builder, Prototype |
| 结构型 (Structural) | Facade, Flyweight, Proxy (AOP) | Adapter, Decorator, Composite |
| 行为型 (Behavioral) | Template Method, Strategy | Observer, Command, Chain of Responsibility, State |

---

## 🏗️ 一、创建型模式 (Creational Patterns)

### 1.1 单例模式 (Singleton) - ✅ 已存在

**现状分析**：
- Spring Bean 默认 Singleton Scope，项目中所有 `@Component`、`@Service` 类均为单例
- `SnowflakeIdGenerator` 通过 Spring 管理，确保全局唯一

**代码位置**：
- [SnowflakeIdGenerator.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/common/utils/SnowflakeIdGenerator.java)

```java
@Component
public class SnowflakeIdGenerator {
    // Spring 单例保证全局唯一
    public synchronized long nextId() { ... }
}
```

---

### 1.2 工厂模式 (Factory) - 🔧 可优化

**优化场景**：策略对象创建

**当前问题**：
- `RedNineTurnStrategy` 和 `GreenNineTurnStrategy` 由 Spring 自动注入
- 缺少统一的策略创建入口，不便于动态加载

**建议实现**：

```java
/**
 * 策略工厂 (Strategy Factory)
 * 使用工厂模式统一创建策略实例
 */
@Component
public class StrategyFactory {
    
    private final Map<String, BaseStrategy> strategyMap;
    
    public StrategyFactory(List<BaseStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(BaseStrategy::getId, Function.identity()));
    }
    
    public BaseStrategy getStrategy(String strategyId) {
        return strategyMap.get(strategyId);
    }
    
    public List<BaseStrategy> getStrategiesByType(StrategyType type) {
        return strategyMap.values().stream()
            .filter(s -> s.getType() == type)
            .toList();
    }
}
```

**文件位置建议**：
- `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/factory/StrategyFactory.java`

---

### 1.3 建造者模式 (Builder) - 🔧 可新增

**应用场景**：复杂 DTO 构建

**当前问题**：
- `HistoryTrendDTO`、`ClosePriceDTO` 等 DTO 使用 Lombok `@Data`
- 复杂对象构建时参数较多，可读性差

**建议实现**：

```java
/**
 * 策略信号建造者 (Strategy Signal Builder)
 * 用于构建策略触发信号对象
 */
@Getter
public class StrategySignal {
    private final String strategyId;
    private final String windCode;
    private final LocalDateTime signalTime;
    private final Double triggerPrice;
    private final SignalType signalType;
    private final Map<String, Object> metadata;
    
    private StrategySignal(Builder builder) {
        this.strategyId = builder.strategyId;
        this.windCode = builder.windCode;
        this.signalTime = builder.signalTime;
        this.triggerPrice = builder.triggerPrice;
        this.signalType = builder.signalType;
        this.metadata = builder.metadata;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String strategyId;
        private String windCode;
        private LocalDateTime signalTime;
        private Double triggerPrice;
        private SignalType signalType;
        private Map<String, Object> metadata = new HashMap<>();
        
        public Builder strategyId(String strategyId) {
            this.strategyId = strategyId;
            return this;
        }
        
        public Builder windCode(String windCode) {
            this.windCode = windCode;
            return this;
        }
        
        public Builder signalTime(LocalDateTime signalTime) {
            this.signalTime = signalTime;
            return this;
        }
        
        public Builder triggerPrice(Double triggerPrice) {
            this.triggerPrice = triggerPrice;
            return this;
        }
        
        public Builder signalType(SignalType signalType) {
            this.signalType = signalType;
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public StrategySignal build() {
            Objects.requireNonNull(strategyId, "strategyId is required");
            Objects.requireNonNull(windCode, "windCode is required");
            return new StrategySignal(this);
        }
    }
}
```

> [!TIP]
> 也可以使用 Lombok 的 `@Builder` 注解简化实现

---

## 🏛️ 二、结构型模式 (Structural Patterns)

### 2.1 外观模式 (Facade) - ✅ 已存在

**现状分析**：
- Service 层作为业务外观，封装 DAO、Cache、Kafka 等底层细节
- 14 个 Service 接口 + Impl 实现类

**代码位置**：
- [QuotationService.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/service/QuotationService.java)
- [StrategyPreparationService.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/service/StrategyPreparationService.java)

```java
public interface QuotationService {
    // 统一对外接口，隐藏内部复杂性
    List<QuotationDTO> getRealtimeQuotation(String windCode);
}
```

---

### 2.2 享元模式 (Flyweight) - ✅ 已存在

**现状分析**：
- `StockCache` 缓存股票基础数据，避免重复加载
- `TradeDateCache` 缓存交易日历，全局共享

**代码位置**：
- [StockCache.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/cache/StockCache.java)
- [TradeDateCache.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/cache/TradeDateCache.java)

```java
@Component
public class StockCache {
    // 全局共享的股票代码映射
    public static Map<String, String> stockIdToWindCodeMap = new HashMap<>();
    public static Map<String, String> windCodeToNameMap = new HashMap<>();
}
```

---

### 2.3 代理模式 (Proxy) / AOP - ✅ 已存在

**现状分析**：
- `OperationLogAspect` 使用 AOP 代理实现操作日志切面
- 无侵入式地增强业务方法

**代码位置**：
- [OperationLogAspect.java](file:///e:/project/quant-nano-alpha/services/quant-data-archive/src/main/java/com/quant/data/archive/aspect/OperationLogAspect.java)

```java
@Aspect
@Component
public class OperationLogAspect {
    
    @Around("@annotation(OperationAudit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        // 代理增强：记录操作日志
    }
}
```

---

### 2.4 适配器模式 (Adapter) - 🔧 可新增

**应用场景**：外部数据源适配

**当前问题**：
- 不同数据源（股票行情、财务数据）格式不统一
- 需要适配层转换为内部 DTO

**建议实现**：

```java
/**
 * 行情数据适配器接口
 */
public interface QuotationAdapter<T> {
    HistoryTrendDTO adapt(T externalData);
}

/**
 * Wind数据源适配器
 */
@Component
public class WindQuotationAdapter implements QuotationAdapter<WindQuotationVO> {
    @Override
    public HistoryTrendDTO adapt(WindQuotationVO vo) {
        return HistoryTrendDTO.builder()
            .windCode(vo.getWindCode())
            .latestPrice(vo.getClose())
            .tradeDate(parseDateTime(vo.getTradeTime()))
            .build();
    }
}
```

---

### 2.5 装饰器模式 (Decorator) - 🔧 可新增

**应用场景**：策略增强

**当前问题**：
- 策略需要动态添加功能（如：日志、监控、限流）
- 硬编码会导致类爆炸

**建议实现**：

```java
/**
 * 策略装饰器基类
 */
public abstract class StrategyDecorator extends BaseStrategy {
    protected final BaseStrategy wrappedStrategy;
    
    public StrategyDecorator(BaseStrategy strategy) {
        this.wrappedStrategy = strategy;
    }
}

/**
 * 监控装饰器：添加指标采集
 */
public class MonitoringDecorator extends StrategyDecorator {
    private final MeterRegistry meterRegistry;
    
    @Override
    public boolean isMatch(HistoryTrendDTO dto) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return wrappedStrategy.isMatch(dto);
        } finally {
            sample.stop(meterRegistry.timer("strategy.execution", "id", getId()));
        }
    }
}
```

---

## 🎭 三、行为型模式 (Behavioral Patterns)

### 3.1 模板方法模式 (Template Method) - ✅ 已存在

**现状分析**：
- `BaseStrategy` 定义策略骨架
- `AbstractNineTurnStrategy` 定义九转序列通用流程
- `RedNineTurnStrategy`/`GreenNineTurnStrategy` 实现具体公式

**代码位置**：
- [BaseStrategy.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/BaseStrategy.java)
- [AbstractNineTurnStrategy.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/impl/nineturn/AbstractNineTurnStrategy.java)

```java
public abstract class AbstractNineTurnStrategy extends BaseStrategy {
    
    // 模板方法：定义执行流程
    public boolean isMatch(HistoryTrendDTO dto) {
        // 1. 校验交易日
        // 2. 校验收盘时间
        // 3. 获取历史数据
        // 4. 调用抽象方法 checkFormula() - 由子类实现
        return checkFormula(prices);
    }
    
    // 抽象钩子：由子类实现
    protected abstract boolean checkFormula(List<Double> prices);
}
```

---

### 3.2 策略模式 (Strategy) - ✅ 已存在

**现状分析**：
- `ScoreZone` 枚举使用策略模式计算分数
- 每个枚举值实现自己的 `calculateScore()` 方法

**代码位置**：
- [MarketSentimentScorer.java](file:///e:/project/quant-nano-alpha/services/quant-risk-control/src/main/java/com/hao/riskcontrol/common/enums/market/MarketSentimentScorer.java)
- [ScoreZone.java](file:///e:/project/quant-nano-alpha/services/quant-risk-control/src/main/java/com/hao/riskcontrol/common/enums/market/ScoreZone.java)

---

### 3.3 观察者模式 (Observer) - 🔧 可优化

**应用场景**：策略信号通知

**当前问题**：
- `onSignalTriggered()` 仅打印日志
- 需要支持多种通知方式（微信、钉钉、数据库记录）

**建议实现**：

```java
/**
 * 信号观察者接口
 */
public interface SignalObserver {
    void onSignal(StrategySignal signal);
}

/**
 * 策略调度器增强版
 */
@Component
public class ObservableStrategyDispatcher extends StrategyDispatcher {
    
    private final List<SignalObserver> observers;
    
    public ObservableStrategyDispatcher(List<BaseStrategy> strategies,
                                        ThreadPoolTaskExecutor executor,
                                        List<SignalObserver> observers) {
        super(strategies, executor);
        this.observers = observers;
    }
    
    @Override
    protected void executeStrategy(BaseStrategy strategy, HistoryTrendDTO dto) {
        boolean matched = strategy.isMatch(dto);
        if (matched) {
            StrategySignal signal = buildSignal(strategy, dto);
            notifyObservers(signal);
        }
    }
    
    private void notifyObservers(StrategySignal signal) {
        observers.forEach(observer -> observer.onSignal(signal));
    }
}

// 具体观察者实现
@Component
public class WeChatNotifyObserver implements SignalObserver {
    @Override
    public void onSignal(StrategySignal signal) {
        // 发送微信通知
    }
}

@Component
public class DatabaseRecordObserver implements SignalObserver {
    @Override
    public void onSignal(StrategySignal signal) {
        // 记录到数据库
    }
}
```

---

### 3.4 命令模式 (Command) - 🔧 可新增

**应用场景**：策略执行命令封装

**当前问题**：
- 策略执行逻辑与调用耦合
- 不便于实现撤销、重放、队列化

**建议实现**：

```java
/**
 * 策略执行命令接口
 */
public interface StrategyCommand {
    void execute();
    void undo();
    String getCommandId();
}

/**
 * 九转策略执行命令
 */
public class NineTurnStrategyCommand implements StrategyCommand {
    private final AbstractNineTurnStrategy strategy;
    private final HistoryTrendDTO dto;
    private boolean executed = false;
    
    @Override
    public void execute() {
        if (strategy.isMatch(dto)) {
            strategy.onSignalTriggered(dto);
            executed = true;
        }
    }
    
    @Override
    public void undo() {
        if (executed) {
            // 撤销信号记录
        }
    }
}

/**
 * 命令调用者
 */
@Component
public class StrategyCommandInvoker {
    private final Queue<StrategyCommand> commandQueue = new ConcurrentLinkedQueue<>();
    
    public void submit(StrategyCommand command) {
        commandQueue.offer(command);
    }
    
    public void executeAll() {
        while (!commandQueue.isEmpty()) {
            commandQueue.poll().execute();
        }
    }
}
```

---

### 3.5 责任链模式 (Chain of Responsibility) - 🔧 可新增

**应用场景**：策略前置校验链

**当前问题**：
- `isMatch()` 中的校验逻辑硬编码
- 新增校验条件需要修改基类

**建议实现**：

```java
/**
 * 策略校验处理器
 */
public interface StrategyValidator {
    boolean validate(HistoryTrendDTO dto);
    void setNext(StrategyValidator next);
}

/**
 * 交易日校验器
 */
@Component
@Order(1)
public class TradingDayValidator implements StrategyValidator {
    private StrategyValidator next;
    
    @Override
    public boolean validate(HistoryTrendDTO dto) {
        if (!isTradingDay(dto.getTradeDate().toLocalDate())) {
            return false;
        }
        return next != null ? next.validate(dto) : true;
    }
    
    @Override
    public void setNext(StrategyValidator next) {
        this.next = next;
    }
}

/**
 * 收盘时间校验器
 */
@Component
@Order(2)
public class MarketCloseValidator implements StrategyValidator {
    private StrategyValidator next;
    
    @Override
    public boolean validate(HistoryTrendDTO dto) {
        if (!isMarketClosed(dto.getTradeDate())) {
            return false;
        }
        return next != null ? next.validate(dto) : true;
    }
}

/**
 * 校验链构建器
 */
@Component
public class ValidatorChainBuilder {
    
    public StrategyValidator buildChain(List<StrategyValidator> validators) {
        for (int i = 0; i < validators.size() - 1; i++) {
            validators.get(i).setNext(validators.get(i + 1));
        }
        return validators.get(0);
    }
}
```

---

### 3.6 状态模式 (State) - 🔧 可新增

**应用场景**：策略状态管理

**当前问题**：
- 策略可能有不同状态（启用、禁用、预热中）
- 状态转换逻辑分散

**建议实现**：

```java
/**
 * 策略状态接口
 */
public interface StrategyState {
    boolean canExecute();
    void onEnter(BaseStrategy context);
    void onExit(BaseStrategy context);
}

/**
 * 策略状态枚举
 */
public enum StrategyStateEnum implements StrategyState {
    WARMING_UP {
        @Override
        public boolean canExecute() { return false; }
        
        @Override
        public void onEnter(BaseStrategy context) {
            log.info("策略进入预热状态|Strategy_warming_up,id={}", context.getId());
        }
    },
    
    ACTIVE {
        @Override
        public boolean canExecute() { return true; }
    },
    
    DISABLED {
        @Override
        public boolean canExecute() { return false; }
    }
}

/**
 * 有状态策略基类
 */
public abstract class StatefulStrategy extends BaseStrategy {
    private StrategyState currentState = StrategyStateEnum.WARMING_UP;
    
    public void transitionTo(StrategyState newState) {
        currentState.onExit(this);
        currentState = newState;
        currentState.onEnter(this);
    }
    
    @Override
    public boolean isMatch(HistoryTrendDTO dto) {
        if (!currentState.canExecute()) {
            return false;
        }
        return doMatch(dto);
    }
    
    protected abstract boolean doMatch(HistoryTrendDTO dto);
}
```

---

## 📋 四、实施优先级建议

| 优先级 | 模式 | 应用场景 | 实施难度 | 收益 |
|-------|------|---------|---------|-----|
| 🔴 P0 | 观察者模式 | 信号通知多渠道 | 低 | 高 |
| 🔴 P0 | 工厂模式 | 策略统一创建 | 低 | 中 |
| 🟡 P1 | 责任链模式 | 校验逻辑解耦 | 中 | 高 |
| 🟡 P1 | 建造者模式 | 信号对象构建 | 低 | 中 |
| 🟢 P2 | 装饰器模式 | 策略功能增强 | 中 | 中 |
| 🟢 P2 | 状态模式 | 策略生命周期 | 高 | 中 |
| 🟢 P2 | 命令模式 | 执行可追溯 | 高 | 中 |
| ⚪ P3 | 适配器模式 | 外部数据源 | 中 | 低 |

---

## 🎯 总结

### 已存在的设计模式 (6个)

1. **单例模式** - Spring Bean 默认单例
2. **外观模式** - Service 层统一封装
3. **享元模式** - Cache 类全局共享
4. **代理模式** - AOP 切面增强
5. **模板方法模式** - 策略抽象类骨架
6. **策略模式** - ScoreZone 评分策略

### 建议新增的设计模式 (7个)

1. **工厂模式** - 策略统一创建入口
2. **建造者模式** - 信号对象构建
3. **适配器模式** - 外部数据源转换
4. **装饰器模式** - 策略动态增强
5. **观察者模式** - 信号多渠道通知
6. **责任链模式** - 校验逻辑链
7. **状态模式** - 策略生命周期管理

> [!IMPORTANT]
> 以上建议需根据实际业务需求选择性实施，避免过度设计。
