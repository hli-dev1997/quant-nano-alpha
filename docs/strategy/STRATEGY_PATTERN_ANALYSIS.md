# 策略模块设计模式分析报告

> **文档版本**: v1.0  
> **更新日期**: 2026-02-04  
> **作者**: hli

---

## 一、整体架构

策略模块采用 **经典策略模式 + 模板方法模式** 的组合设计，整体评分：⭐⭐⭐⭐☆ (4/5)

### 1.1 类图结构

```
┌─────────────────────────────────────────────────────────────────┐
│                     StrategyDispatcher                          │
│         (Context - 策略调度器，持有所有策略实例)                    │
└─────────────────────────┬───────────────────────────────────────┘
                          │ dispatch() 分发行情数据
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      BaseStrategy                               │
│         (抽象策略 + 模板方法：isMatch/onSignalTriggered)            │
└─────────────────────────┬───────────────────────────────────────┘
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│AbstractNineTurn│ │AbstractMoving  │ │  其他策略...    │
│   Strategy     │ │AverageStrategy │ │                │
└───────┬────────┘ └───────┬────────┘ └────────────────┘
   ┌────┴────┐        ┌────┴────┐
   ▼         ▼        ▼         ▼
RedNine  GreenNine BullishMA BearishMA
```

### 1.2 核心组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `StrategyDispatcher` | `strategy-engine/.../StrategyDispatcher.java` | 策略上下文，负责分发行情数据到所有策略 |
| `BaseStrategy` | `strategy-engine/.../BaseStrategy.java` | 抽象策略基类，封装公共能力 |
| `AbstractNineTurnStrategy` | `strategy-engine/.../impl/nineturn/` | 九转策略二级抽象 |
| `AbstractMovingAverageStrategy` | `strategy-engine/.../impl/movingaverage/` | 均线策略二级抽象 |
| `StrategyPreheater` | `data-collector/.../preheat/StrategyPreheater.java` | 策略预热器接口 |
| `StrategyPreheaterManager` | `data-collector/.../preheat/StrategyPreheaterManager.java` | 预热器管理器 |

---

## 二、设计亮点

### 2.1 开闭原则 (OCP) ✅

新增策略只需继承 `BaseStrategy`，无需修改 `StrategyDispatcher`：

```java
// 新增策略示例
@Component
public class NewStrategy extends BaseStrategy {
    @Override
    public String getId() { return "NEW_STRATEGY"; }
    
    @Override
    public boolean isMatch(HistoryTrendDTO dto) {
        // 实现策略逻辑
    }
}
```

### 2.2 Spring 自动发现 ✅

利用 Spring 依赖注入自动收集所有策略实现：

```java
@Component
public class StrategyDispatcher {
    // Spring 自动注入所有 BaseStrategy 实现
    private final List<BaseStrategy> strategies;
}
```

### 2.3 模板方法复用 ✅

公共逻辑封装在基类，子类只需实现差异点：

| 基类封装能力 | 子类实现点 |
|-------------|-----------|
| 交易日历加载 (`@PostConstruct`) | 策略ID (`getId()`) |
| 交易日校验 (`isTradingDay`) | 信号类型 (`getSignalType()`) |
| Kafka 信号发送 (`onSignalTriggered`) | 匹配逻辑 (`isMatch()`) |
| DTO 构建 (`buildSignalDTO`) | 公式判断 (`checkFormula()`) |

### 2.4 二级抽象层 ✅

同类策略共享逻辑，减少代码重复：

```
BaseStrategy
    ├── AbstractNineTurnStrategy      # 封装九转公共逻辑（Redis读取、14日校验）
    │       ├── RedNineTurnStrategy   # 红九公式
    │       └── GreenNineTurnStrategy # 绿九公式
    │
    └── AbstractMovingAverageStrategy # 封装均线公共逻辑（MA计算）
            ├── BullishMAStrategy     # 多头排列
            └── BearishMAStrategy     # 空头排列
```

### 2.5 线程池并行执行 ✅

策略在独立线程池中并行执行，不阻塞 Kafka 消费：

```java
public void dispatch(HistoryTrendDTO dto) {
    for (BaseStrategy strategy : strategies) {
        strategyExecutor.execute(() -> executeStrategy(strategy, dto));
    }
}
```

### 2.6 异常隔离 ✅

单个策略失败不影响其他策略执行：

```java
private void executeStrategy(BaseStrategy strategy, HistoryTrendDTO dto) {
    try {
        boolean matched = strategy.isMatch(dto);
        if (matched) {
            strategy.onSignalTriggered(dto);
        }
    } catch (Exception e) {
        log.error("策略执行异常|Strategy_execution_error,strategy={}", strategy.getId(), e);
    }
}
```

---

## 三、存在问题与改进建议

### 3.1 策略接口未定义

**现状**：使用纯抽象类 `BaseStrategy`

**问题**：不符合策略模式经典定义（接口 + 实现）

**建议**：抽取接口 `IStrategy`：

```java
public interface IStrategy {
    String getId();
    SignalTypeEnum getSignalType();
    boolean isMatch(HistoryTrendDTO dto);
    void onSignalTriggered(HistoryTrendDTO dto);
}

public abstract class BaseStrategy implements IStrategy {
    // 保持现有实现
}
```

### 3.2 策略优先级缺失

**现状**：所有策略平等执行，无法控制顺序

**建议**：实现 `Ordered` 接口或使用 `@Order` 注解：

```java
@Component
@Order(1)  // 优先级最高
public class RedNineTurnStrategy extends AbstractNineTurnStrategy {
}
```

### 3.3 策略动态开关缺失

**现状**：无法动态启用/禁用策略，需重新部署

**建议**：结合 Nacos 配置中心实现热开关：

```java
@Component
public class RedNineTurnStrategy extends AbstractNineTurnStrategy {
    
    @Value("${strategy.nineTurn.red.enabled:true}")
    private boolean enabled;
    
    @Override
    public boolean isMatch(HistoryTrendDTO dto) {
        if (!enabled) {
            return false;  // 策略已禁用
        }
        return super.isMatch(dto);
    }
}
```

### 3.4 策略元数据分散

**现状**：策略ID、名称、风险等级分散在各子类中

**建议**：完善 `StrategyMetaEnum` 统一注册：

```java
public enum StrategyMetaEnum {
    SIG_NINE_TURN_RED("NINE_TURN_RED", "红九", StrategyRiskLevelEnum.HIGH),
    SIG_NINE_TURN_GREEN("NINE_TURN_GREEN", "绿九", StrategyRiskLevelEnum.HIGH),
    SIG_MA_BULLISH("MA_BULLISH", "多头排列", StrategyRiskLevelEnum.MEDIUM);
    
    private final String id;
    private final String name;
    private final StrategyRiskLevelEnum riskLevel;
}
```

---

## 四、代码质量评估

### 4.1 规范符合度

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 类名 UpperCamelCase | ✅ | `RedNineTurnStrategy`, `BullishMAStrategy` |
| 方法名 lowerCamelCase | ✅ | `isMatch()`, `checkFormula()` |
| Javadoc 注释 | ✅ | 类、方法均有完整注释 |
| SLF4J 日志 | ✅ | 使用 `@Slf4j` + 占位符 |
| 空指针防护 | ✅ | Redis 读取、价格校验有 null 检查 |
| 魔法值消除 | ✅ | 使用 `REQUIRED_HISTORY_DAYS = 13` 常量 |

### 4.2 日志规范

统一采用 `中文描述|English_key` 格式，便于日志检索：

```java
log.info("策略交易日历初始化完成|Strategy_trade_date_init,strategy={},dateCount={}", getId(), tradeDateList.size());
```

---

## 五、扩展指南

### 5.1 新增策略步骤

1. **确定策略类型**：选择继承 `AbstractNineTurnStrategy` 或 `AbstractMovingAverageStrategy`，或直接继承 `BaseStrategy`

2. **实现抽象方法**：
   ```java
   @Component
   public class MyNewStrategy extends BaseStrategy {
       @Override
       public String getId() { return "MY_NEW_STRATEGY"; }
       
       @Override
       public SignalTypeEnum getSignalType() { return SignalTypeEnum.BUY; }
       
       @Override
       public boolean isMatch(HistoryTrendDTO dto) {
           // 实现策略逻辑
       }
   }
   ```

3. **（可选）实现预热器**：
   ```java
   @Component
   public class MyNewPreheater implements StrategyPreheater {
       @Override
       public String getStrategyId() { return "MY_NEW_STRATEGY"; }
       
       @Override
       public int preheat(LocalDate tradeDate, List<String> stockCodes) {
           // 预热历史数据到 Redis
       }
   }
   ```

4. **注册到枚举**（可选）：在 `StrategyMetaEnum` 中添加元数据

### 5.2 策略依赖数据流

```
[DataCollector]                    [StrategyEngine]
      │                                   │
      │  预热历史数据                        │
      ├────────────────► Redis ◄──────────┤ 读取历史数据
      │                                   │
      │  发送实时行情                        │
      ├────────────────► Kafka ──────────►│ 消费行情
      │                                   │
      │                                   │  策略匹配
      │                                   ▼
      │                            StrategyDispatcher
      │                                   │
      │                                   │  触发信号
      │                            Kafka ◄┤
      │                                   │
      └───────────────────────────────────┘
```

---

## 六、总结

| 维度 | 评价 |
|------|------|
| **设计模式运用** | ✅ 策略模式 + 模板方法模式，组合得当 |
| **扩展性** | ✅ 新增策略只需添加子类，符合 OCP |
| **复用性** | ✅ 二级抽象层减少重复代码 |
| **健壮性** | ✅ 异常隔离、空指针防护、日志完善 |
| **待改进** | ⚠️ 接口抽取、动态开关、优先级机制 |

**整体评价**：策略模块设计达到 **中高级水平**，能够支撑真实量化策略引擎场景，后续可根据业务演进持续优化。

---

## 附录：相关文件清单

| 文件 | 说明 |
|------|------|
| [BaseStrategy.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/BaseStrategy.java) | 策略抽象基类 |
| [StrategyDispatcher.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/StrategyDispatcher.java) | 策略调度器 |
| [AbstractNineTurnStrategy.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/impl/nineturn/AbstractNineTurnStrategy.java) | 九转策略抽象类 |
| [AbstractMovingAverageStrategy.java](file:///e:/project/quant-nano-alpha/services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/stream/strategy/impl/movingaverage/AbstractMovingAverageStrategy.java) | 均线策略抽象类 |
| [StrategyPreheater.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/replay/preheat/StrategyPreheater.java) | 预热器接口 |
| [StrategyPreheaterManager.java](file:///e:/project/quant-nano-alpha/services/quant-data-collector/src/main/java/com/hao/datacollector/replay/preheat/StrategyPreheaterManager.java) | 预热器管理器 |
