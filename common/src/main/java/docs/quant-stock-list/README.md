# StockList 子模块技术说明

## 1. 模块定位与整体架构
`quant-stock-list` 是量化平台对外提供“每日稳定策略精选股票”查询能力的独立服务。应用入口位于 `StockListApplication`，启用了缓存、Kafka 与定时调度，用于支撑高并发的读多写少场景。模块遵循“应用层 → 领域层 → 基础设施层”的分层设计：

- **应用层**（`application` 包）暴露 HTTP API、装配 DTO/VO，并做限流与入参校验。
- **领域层**（`domain` 包）封装核心业务流程，如多级缓存、布隆过滤器、分布式锁及缓存预热等逻辑。
- **基础设施层**（`infrastructure` 包）对接 MySQL、Redis、Kafka、Redisson、Caffeine 等外部组件，并提供配置、数据持久化、消息消费与监控能力。

## 2. 目录结构与文件说明
```
quant-stock-list
├── src/main/java/com/hao/quant/stocklist
│   ├── StockListApplication.java        # Spring Boot 启动入口
│   ├── application                      # Controller、Assembler、DTO/VO
│   ├── common                           # 通用响应与异常
│   ├── domain                           # 领域模型、服务、仓储接口
│   └── infrastructure                   # 缓存、配置、消息、持久化、任务、指标
├── src/main/resources
│   ├── application.yml                  # 示例配置
│   ├── mapper/StablePicksMapper.xml     # MyBatis SQL
│   ├── schema/strategy_daily_picks.sql  # 建表脚本
│   └── docs/quant-stock-list-handbook.md
└── pom.xml                              # Maven 依赖与构建配置
```

## 3. 请求入口与数据流
以下流程描述 HTTP 请求从进入 Controller 到返回响应的完整链路，并精确到关键类与方法。

### 3.1 GET `/api/v1/stable-picks/daily`
1. **接口入口**：`StablePicksController#queryDailyPicks` 负责接收交易日、策略、行业与分页参数，限流交由 Resilience4j 处理。
2. **组装参数**：Controller 将参数封装为 `StablePicksQueryDTO`，便于后续统一校验与缓存 Key 生成。
3. **领域服务**：调用 `StablePicksServiceImpl#queryDailyPicks` 执行业务流程。
   - 校验分页参数、判空交易日，并在非法入参时抛出 `BusinessException`。
   - 调用 `StablePicksBloomFilter#mightContainTradeDate` 做交易日存在性校验，拦截非法日期请求。
   - 使用 `StablePicksCacheKeyBuilder#buildDailyKey` 拼接缓存 Key，并通过模板方法 `queryWithCache` 查询多级缓存。
4. **多级缓存访问**：`queryWithCache`
   - 先检查 Caffeine 本地缓存 `StablePicksCacheRepository#getLocal`，未命中则查 Redis `getDistributed`。
   - 缓存进入软过期阶段时，通过 `TaskScheduler` 异步触发刷新 `triggerAsyncRefreshIfNecessary`，避免请求线程阻塞。
   - 本地与分布式缓存均未命中时，借助 `StablePicksLockManager#executeWithLock` 在 Redisson 分布式锁保护下回源，防止击穿。
5. **数据库回源**：锁内调用 `loadDailyCache`
   - 通过领域仓储接口 `StablePicksRepository#queryDaily/countDaily` 拿到数据。
   - 仓储实现 `MyBatisStablePicksRepository` 调用 `StablePicksMapper` 执行 SQL 并将 PO 映射为领域模型 `StablePick`。
   - `StablePicksAssembler` 将 `StablePick` 转换为 VO，同时序列化扩展字段。
   - 结果包装成 `CacheWrapper<PageResult<StablePicksVO>>` 并写回 Caffeine 与 Redis，TTL 由 `redisTtl` 配置控制，附带随机抖动避免雪崩。
6. **返回响应**：Controller 使用 `Result.success` 统一包装分页数据，返回给前端。

### 3.2 GET `/api/v1/stable-picks/latest`
- Controller 入口 `StablePicksController#queryLatestPicks` 接受策略和条数参数。
- 领域服务 `StablePicksServiceImpl#queryLatestPicks` 生成缓存 Key（策略 + limit），复用 `queryWithCache` 模板；回源时 `loadLatestCache` 查询仓储并刷新布隆过滤器。
- 返回结果为 `Result<List<StablePicksVO>>`。

### 3.3 GET `/api/v1/stable-picks/detail/{stockCode}`
- Controller 入口 `queryStockDetail` 接受股票代码与交易日，限流兜底在 `rateLimitDetailFallback`。
- 领域服务 `StablePicksServiceImpl#queryStockDetail` 校验交易日并调用布隆过滤器后，通过 `buildDetailKey` 访问缓存；回源逻辑 `loadDetailCache` 查询仓储 `findDetail` 并回写缓存。

### 3.4 运维接口
- `POST /cache/refresh`：`StablePicksController#refreshCache` 调用 `manualRefreshCache`，主动回源指定日期/策略的日度与最新缓存。
- `POST /cache/warmup`：`StablePicksController#warmupCache` 触发 `warmupCache`，用于开盘前装载热点交易日数据。

## 4. 事件驱动的数据刷新链路
1. **写入侧**：策略计算完成后由 `StrategyResultWriter#batchSave/saveOne` 将结果落库，并根据数据构造 `StrategyResultEvent` 发送到 Kafka 主题 `strategy.result.completed`。
2. **消息消费**：查询侧的 `StrategyResultEventListener#onMessage` 监听该主题，收到事件后：
   - 回写布隆过滤器，确保新交易日可被查询；
   - 根据交易日构造模式批量删除日度、最新、详情缓存；
   - 逐条调用 `StablePicksCacheRepository#evict` 清理 Caffeine + Redis 缓存，保证后续请求命中新数据。
3. **回源刷新**：缓存被清理后，下一次查询会按照第 3 节流程重新加载数据，实现“写入驱动读侧刷新”的最终一致性。

## 5. 核心业务组件与方法说明
| 类 / 方法 | 作用 | 输入参数 | 输出 / 异常 |
|-----------|------|----------|-------------|
| `StablePicksServiceImpl#queryDailyPicks` | 带分页的日度查询主流程 | `StablePicksQueryDTO` | `PageResult<StablePicksVO>`；非法日期抛 `BusinessException` |
| `StablePicksServiceImpl#queryLatestPicks` | 最新交易日聚合查询 | `strategyId`、`limit` | `List<StablePicksVO>` |
| `StablePicksServiceImpl#queryStockDetail` | 单支股票详情查询 | `stockCode`、`tradeDate` | `StablePicksVO` 或 `null`；非法日期抛 `BusinessException` |
| `StablePicksServiceImpl#manualRefreshCache` | 手动刷新缓存 | `tradeDate`、`strategyId` | `void`；空日期抛异常 |
| `StablePicksServiceImpl#warmupCache` | 定时/手动预热热点数据 | `tradeDate` | `void`；空日期抛异常 |
| `StablePicksRepository` | 领域仓储契约，抽象 DB 访问 | 交易日、策略、行业、分页 | 领域模型列表 / 计数 / 详情等 |
| `StablePicksCacheRepository` | 多级缓存统一读写 | `cacheKey`、`CacheWrapper` | Optional 包装的缓存数据，异常时清理错误缓存 |
| `StablePicksBloomFilter` | Redis BitMap 布隆过滤器 | `tradeDate` | 存在性校验、写入布隆位图 |
| `StablePicksLockManager#executeWithLock` | Redisson 分布式锁封装 | `lockKey`、等待/租约时间、成功/失败回调 | 在锁保护下执行回源逻辑，异常或获取失败走降级 |

## 6. 缓存策略与并发控制
- **多级缓存**：`StablePicksCacheRepository` 使用 Caffeine（L1）+ Redis（L2）组合，本地缓存 5 分钟过期，Redis TTL 可通过配置 `stable-picks.cache.redis-ttl` 调整，写入时附加随机秒数防止雪崩。
- **软过期异步刷新**：`CacheWrapper#shouldRefreshAsync` 在缓存过半生命周期后启动后台刷新，线程池由 `stablePicksScheduler` 提供，减轻请求线程压力。
- **布隆过滤器**：基于 Redis BitMap 记录交易日，降低无效请求造成的穿透；写入侧、消息监听与预热任务都会补充布隆数据。
- **分布式锁**：回源时通过 Redisson 锁保护数据库访问，并在获取失败或中断时回退到缓存兜底，避免缓存击穿导致的 DB 洪峰。

## 7. 数据持久化层
- **Mapper & SQL**：`StablePicksMapper.xml` 定义了查询日度列表、最新列表、详情、批量插入、最近交易日等 SQL，均基于 `strategy_daily_picks` 表。
- **PO/TypeHandler**：`StrategyDailyPickPO` 映射数据库字段；`JsonMapTypeHandler` 负责 JSON ↔ Map 转换处理 `extra_data` 扩展字段。
- **建表脚本**：`strategy_daily_picks.sql` 包含主表、交易日历、策略元数据及缓存刷新日志的建表语句，方便初始化数据库环境。

## 8. 运行机制与定时任务
- **缓存预热**：`StablePicksWarmupTask#warmupToday` 每个工作日早上 8 点读取最近交易日并调用领域服务预热缓存，降低开盘瞬时冷启动延迟。
- **监控指标**：`StablePicksMetrics` 将 Caffeine 命中率、淘汰次数及 Redis Key 数量暴露为 Micrometer 指标，便于接入 Prometheus。
- **限流与降级**：Controller 接口使用 Resilience4j `@RateLimiter`，并提供兜底方法返回友好错误码。

## 9. 外部依赖与配置
- **数据库**：MySQL，表结构见 `schema/strategy_daily_picks.sql`；数据访问通过 MyBatis + Hikari 数据源，事务由 `DataSourceTransactionManager` 管理。
- **缓存**：Redis (L2 缓存 + 布隆过滤器) 与 Caffeine (L1 缓存)；Redisson 提供分布式锁。
- **消息队列**：Kafka 主题 `strategy.result.completed` 用于通知缓存刷新。
- **注册配置**：默认使用 Nacos/Sentinel（示例配置写在 `application.yml`）。
- **其他依赖**：Resilience4j（限流）、SpringDoc（OpenAPI 文档）、Micrometer（监控），均在 `pom.xml` 中声明。

## 10. 启动与运行指南
1. **环境准备**：安装 JDK 21、Maven 3.9+，并提供可用的 MySQL、Redis、Kafka、Nacos、Sentinel、Redisson（Redis 集群）等服务。
2. **数据库初始化**：执行 `src/main/resources/schema/strategy_daily_picks.sql` 创建所需表；初始化策略元数据和交易日历数据，以支持布隆过滤器预热。
3. **配置文件**：
   - 通过 Nacos 管理外部化配置（见 `application.yml` 的 `spring.cloud.nacos.config` 设置）。
   - 至少配置数据源、Redis、Kafka、Redisson、Resilience4j 限流策略、`stable-picks.cache.*` TTL 参数等。
4. **启动命令**：在仓库根目录执行 `mvn spring-boot:run -pl services/quant-stock-list -am`，或打包后运行 `java -jar target/quant-stock-list-0.0.1-SNAPSHOT.jar`。
5. **验证接口**：服务启动后访问 `http://localhost:8806/quant-stock-list/swagger-ui.html` 查看 OpenAPI 文档，或直接调用第 3 节所述的 REST 接口。
6. **运维建议**：
   - 配合 Prometheus/Grafana 采集 `StablePicksMetrics` 指标。
   - 启用 Kafka 消费监控以保证缓存刷新链路可用。
   - 根据交易日提前执行 `/cache/warmup` 或等待 `StablePicksWarmupTask` 自动运行。

通过以上说明，开发者或面试官可以快速理解 `stocklist` 子模块的架构、数据流及运行机制，并据此进行扩展与调试。
