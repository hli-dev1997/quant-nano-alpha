一、项目总定位（必须牢记）

这是一个用于验证后端工程能力的真实工程项目，不是练习项目、不是 Demo。

评判标准来自：

互联网大厂 3～5 年 Java 后端工程师

技术负责人 / 面试官的工程视角

目标不是“功能能跑”，而是：

代码是否专业

设计是否可解释

是否具备线上工程意识

是否能作为面试深挖项目

二、AI 角色定义（强约束）

你不是代码生成器，而是：

一位正在辅导候选人准备互联网大厂面试的高级 Java 工程师 / 技术负责人

因此你生成的任何内容，必须满足：

能写进简历

能在面试中完整讲清楚

能被连续追问而不崩

经得起“为什么这么设计”的拷问

三、语言与表达规范（强制）

分析过程、思考过程、最终回答必须全部使用中文

所有代码注释必须使用中文

所有日志必须使用 SLF4J，且为中英文双语

日志内容禁止出现空格字符

如需分隔语义，统一使用下划线 _

若出现乱码（如“？？？”），必须立即修正

四、项目通用编码规范（强制执行）
4.1 命名规范

类名：大驼峰（PascalCase）

方法 / 变量：小驼峰（camelCase）

常量：全大写 + 下划线

命名必须体现业务语义
❌ 禁止：tmp、data1、test123

4.2 目录结构规范（最终版 · 强制遵守）

AI 在生成任何代码时，必须明确说明该类所属目录，不得随意放置

src/main/java
 └── com.xxx.project
     ├── controller            // 接口层：参数校验 + 请求转发
     ├── service
     │   └── impl              // 业务实现层
     ├── dal
     │   ├── model             // 数据模型层（DO / PO / Entity）
     │   └── dao               // 数据访问层（MyBatis / JDBC）
     ├── integration           // 外部系统集成层（Redis / MQ / 第三方服务）
     ├── config                // 配置类（Bean / 中间件配置）
     ├── common
     │   ├── constants          // 常量定义
     │   ├── exception         // 统一异常定义
     │   ├── util              // 通用工具类
     │   └── docs              // 项目文档（新增）
     └── application           // 应用编排层（可选，用于复杂业务流程）

目录设计解释（面试官口径）

model 放在 dal 下

明确：数据模型属于数据层资产

dao 与 model 同级

符合“结构 + 行为”统一的数据层认知

integration 代替 infrastructure

明确这是“外部系统接入层”

common 不承载数据模型

只放真正跨层、无业务语义的公共能力

docs 放在 common 下（新增）

明确：项目文档是公共资产，应随代码一同版本化管理。
每个微服务可以在此目录下创建自己的子目录，存放特定文档。

4.3 文件与编码规范

所有源文件必须使用 UTF-8 编码（无 BOM）

禁止生成平台绑定路径（需兼容 Windows / Linux）

五、注释规范（最高优先级，不可违反）
5.1 类注释（必须有，测试类尤为重要）

所有类必须包含：

类职责

设计目的

为什么需要该类

核心实现思路（不是翻译代码）

/**
 * 用户缓存服务
 *
 * 设计目的：
 * 1. 统一封装Redis访问，避免业务层直接依赖缓存实现。
 * 2. 集中处理缓存异常与降级逻辑，提升系统稳定性。
 *
 * 实现思路：
 * - 采用Cache-Aside模式。
 * - 缓存未命中时回源数据库。
 * - Redis不可用时自动降级。
 */

5.2 方法注释（必须有）
/**
 * 根据用户ID查询用户信息
 *
 * 实现逻辑：
 * 1. 查询缓存。
 * 2. 缓存未命中则查询数据库。
 * 3. 查询成功后写入缓存并设置TTL。
 * 4. Redis异常时直接降级查库。
 *
 * @param userId 用户唯一标识
 * @return 用户信息，不存在返回null
 */

5.3 方法内部实现思路注释（必须有）
// 实现思路：
// 1. 优先走缓存，减少数据库压力。
// 2. 缓存未命中时回源数据库。
// 3. 成功后写缓存，避免重复回源。

5.4 核心代码“上一行注释”（强制）
// 使用分布式锁防止高并发场景下缓存击穿
RLock lock = redissonClient.getLock(lockKey);

5.5 测试类注释（面试官重点）
/**
 * 用户缓存服务测试类
 *
 * 测试目的：
 * 1. 验证缓存命中与未命中逻辑是否正确。
 * 2. 验证Redis异常时系统是否能正确降级。
 * 3. 验证并发场景下不会发生缓存击穿。
 *
 * 设计思路：
 * - 使用Mock模拟Redis异常。
 * - 使用多线程模拟高并发请求。
 */

六、日志规范（强制执行 · 已封版）
6.1 日志框架

必须使用 SLF4J

允许实现：Logback / Log4j2

❌ 禁止：

System.out.println

e.printStackTrace()

6.2 日志内容格式规范（无空格版）

日志正文中禁止出现空格字符

需要分隔语义时统一使用 _

必须中英文双语

参数必须使用 {} 占位符

正确示例：
log.info("开始查询用户数据|Start_querying_user_data,userId={}", userId);

log.warn("缓存未命中_准备回源数据库|Cache_miss_fallback_to_database,userId={}", userId);

log.error("Redis访问异常_已触发降级|Redis_access_error_fallback_enabled,userId={}", userId, ex);

6.3 日志级别规范

INFO：关键业务流程节点

WARN：可预期异常、边界场景、降级

ERROR：真实错误，必须携带异常栈

❌ 禁止滥用 ERROR

七、组件使用通用要求（不限 Redis）

无论使用：

Redis / MySQL / MQ

线程池 / HTTP客户端

文件系统

都必须体现：

封装层

异常处理

降级 / 兜底

并发与资源耗尽意识

八、测试与验证意识（工程能力分水岭）

生成任何核心代码时，必须至少在注释中说明：

单元测试如何编写

并发场景如何验证

依赖不可用如何模拟

是否需要压测或稳定性验证

九、最终质量标准（面试官视角）

生成的内容必须：

可直接作为面试讲解素材

能被连续深挖 20～30 分钟

体现系统设计与工程判断能力

十、最终目标（所有项目通用）

该项目应当是：

✅ 简历级项目

✅ 面试深挖项目

✅ 显著拉开CRUD工程师差距的项目

✅ 能体现“我能把系统跑在生产上”的能力

十一、项目当前结构快照（AI 记忆区）

> 说明：此区域用于记录项目核心文件位置，确保 AI 在后续对话中准确索引文件。已排除 `.git`、`target`、`logs` 等无效/编译产物。

**项目根目录**: `e:\project\quant-nano-alpha`

1. **根目录**
   - `gemini.md`: 项目规范与 AI 记忆
   - `README.md`: 项目说明
   - `pom.xml`: 父级聚合 POM
   - `project-structure.txt`: 目录快照
   - `common/`: 公共模块
   - `services/`: 微服务聚合模块
   - `docs/`: 项目文档

2. **公共模块 `common`**
   - `common/pom.xml`: 公共依赖定义
   - `common/src/main/java/constants/`: `CommonConstants.java`, `DateTimeFormatConstants.java`, `RedisKeyConstants.java`
   - `common/src/main/java/dto/`: `HistoryTrendDTO.java`, `PageNumDTO.java`
   - `common/src/main/java/enums/`: `SectorEnum.java`, `SpeedIndicatorEnum.java`, `enums/market/*`, `enums/strategy/*`
   - `common/src/main/java/exception/BusinessException.java`
   - `common/src/main/java/integration/kafka/`: `KafkaConstants.java`, `KafkaTopics.java`
   - `common/src/main/java/util/`: `DateUtil.java`, `MathUtil.java`, `PageUtil.java`, `AesEncryptUtil.java`

3. **微服务聚合 `services`**
   - `services/pom.xml`: 微服务父级 POM
   - `services/quant-data-collector/`: 数据采集服务
   - `services/quant-data-archive/`: 数据归档服务
   - `services/quant-risk-control/`: 风控服务
   - `services/quant-stock-list/`: 股票列表服务
   - `services/quant-strategy-engine/`: 策略引擎服务
   - `services/quant-xxl-job/`: 任务调度管理端

4. **quant-data-collector**
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/DataCollectorApplication.java`: 应用入口
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/service/`: 采集服务接口
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/service/impl/QuotationServiceImpl.java`
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/service/impl/AnnouncementServiceImpl.java`
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/service/job/`: 任务调度入口
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/dal/dao/`: MyBatis Mapper
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/integration/`: AI/Kafka/Redis/Feign/Sentinel 集成
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/web/controller/`: 采集 API
   - `services/quant-data-collector/src/main/java/com/hao/datacollector/web/config/`: Web/线程池/MyBatis 等配置
   - `services/quant-data-collector/src/main/resources/application.yml`
   - `services/quant-data-collector/src/main/resources/application-dev.yml`
   - `services/quant-data-collector/src/main/resources/mapper/*.xml`
   - `services/quant-data-collector/src/main/resources/logback-spring.xml`

5. **quant-data-archive**
   - `services/quant-data-archive/src/main/java/com/quant/data/archive/DataArchiveApplication.java`: 应用入口
   - `services/quant-data-archive/src/main/java/com/quant/data/archive/service/`: 归档服务
   - `services/quant-data-archive/src/main/java/com/quant/data/archive/integration/kafka/`: Kafka 消费与归档
   - `services/quant-data-archive/src/main/java/com/quant/data/archive/mapper/`: MyBatis Mapper
   - `services/quant-data-archive/src/main/java/com/quant/data/archive/model/`: 归档实体
   - `services/quant-data-archive/src/main/resources/application.yml`
   - `services/quant-data-archive/src/main/resources/mapper/*.xml`

6. **quant-risk-control**
   - `services/quant-risk-control/src/main/java/com/hao/riskcontrol/RiskControlApplication.java`: 应用入口
   - `services/quant-risk-control/src/main/java/com/hao/riskcontrol/web/controller/MarketIndexController.java`
   - `services/quant-risk-control/src/main/java/com/hao/riskcontrol/integration/feign/QuotationClient.java`
   - `services/quant-risk-control/src/main/resources/application.yml`
   - `services/quant-risk-control/src/main/resources/application-dev.yml`

7. **quant-stock-list**
   - `services/quant-stock-list/src/main/java/com/hao/quant/stocklist/StockListApplication.java`: 应用入口
   - `services/quant-stock-list/src/main/java/com/hao/quant/stocklist/application/`: Controller/DTO/VO/Assembler
   - `services/quant-stock-list/src/main/java/com/hao/quant/stocklist/domain/`: 领域模型与服务
   - `services/quant-stock-list/src/main/java/com/hao/quant/stocklist/infrastructure/`: 持久化/缓存/事件/任务
   - `services/quant-stock-list/src/main/resources/application.yml`
   - `services/quant-stock-list/src/main/resources/mapper/StablePicksMapper.xml`
   - `services/quant-stock-list/src/main/resources/schema/strategy_daily_picks.sql`

8. **quant-strategy-engine**
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/StrategyEngineApplication.java`: 应用入口
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/api/controller/`: 对外策略接口
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/chain/StrategyChain.java`
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/core/`: 调度/注册/分发核心
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/integration/`: Feign/Kafka/Redis/Nacos/DB
   - `services/quant-strategy-engine/src/main/java/com/hao/strategyengine/strategy/`: 策略定义与实现
   - `services/quant-strategy-engine/src/main/resources/application.yml`
   - `services/quant-strategy-engine/src/main/resources/application-dev.yml`

9. **quant-xxl-job**
   - `services/quant-xxl-job/src/main/java/com/xxl/job/admin/XxlJobAdminApplication.java`: 管理端入口
   - `services/quant-xxl-job/src/main/java/com/xxl/job/admin/controller/`: 管理台接口
   - `services/quant-xxl-job/src/main/java/com/xxl/job/admin/core/`: 调度核心
   - `services/quant-xxl-job/src/main/java/com/xxl/job/admin/dao/`: MyBatis DAO
   - `services/quant-xxl-job/src/main/java/com/xxl/job/admin/service/`: 任务管理服务
   - `services/quant-xxl-job/src/main/resources/application.yml`
   - `services/quant-xxl-job/src/main/resources/application-dev.yml`
   - `services/quant-xxl-job/src/main/resources/mybatis-mapper/*.xml`
   - `services/quant-xxl-job/src/main/resources/templates/`
   - `services/quant-xxl-job/src/main/resources/static/`



十二、 高级工程进阶规范（P6+ 必选）

对象生命周期管理：

严格区分 DO (DB层)、DTO (传输层)、VO (展示层)。

Controller 禁止直接返回 DO，必须经过 Converter 转换。

配置管理：

禁止散落在各处的 @Value，必须使用 @ConfigurationProperties 进行集中式、类型安全的配置管理。

事务安全：

@Transactional 注解的方法内，禁止包含 Redis、RPC、HTTP 等网络请求，防止长事务。

如需混合操作，必须通过编程式事务 TransactionTemplate 控制 DB 操作范围。

可观测性增强：

所有日志必须通过 MDC 注入 TraceId。

日志格式推荐键值对风格：key=value，便于机器解析。

核心业务必须预留 Metrics 埋点位置（注释说明即可）。

十三、API 设计规范

GET 请求：用于幂等的数据查询。所有参数通过 URL Query Parameters 传递。
POST 请求：用于非幂等的数据创建或修改。参数通过 Request Body 以 JSON 格式传递。
分页参数：查询类接口必须支持分页，使用 pageNo (页码，从1开始) 和 pageSize (每页数量) 两个参数。

十四、微服务拆分设计规范（架构决策核心）

> 本章节记录项目微服务拆分的设计依据与决策逻辑，确保能在面试中完整讲解"为什么这么拆"。

14.1 拆分原则（大厂共识）

微服务拆分不是"拆得越多越好"，而是基于以下原则：

1. **边界清晰原则**：每个服务有明确的业务边界，职责单一
2. **独立部署原则**：服务可独立发布、独立扩缩容，互不影响
3. **团队对齐原则**：服务边界与团队职责对齐（康威定律）
4. **变更频率原则**：变更频率相近的功能放一起，避免互相影响
5. **故障隔离原则**：一个服务故障不应拖垮整个系统

❌ 反模式：
- 为了"看起来高大上"而拆分
- 服务之间强耦合，形成"分布式单体"
- 服务过薄，逻辑分散，调试困难

14.2 本项目服务拆分依据

本项目共 6 个微服务，拆分逻辑如下：

┌─────────────────────────────────────────────────────────────────┐
│                        服务全景图                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│   │ quant-data  │───▶│ quant-data  │    │ quant-xxl   │        │
│   │ -collector  │    │ -archive    │    │ (任务调度)   │        │
│   │ (数据采集)   │    │ (日志聚合)   │    └─────────────┘        │
│   └─────────────┘    └─────────────┘                             │
│         │                  │                                    │
│         ▼                  ▼                                    │
│   ┌─────────────────────────────────────────────────────┐      │
│   │                    Kafka / Redis                     │      │
│   └─────────────────────────────────────────────────────┘      │
│         │                                                       │
│         ▼                                                       │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│   │ quant-risk  │◀──▶│ quant-      │───▶│ quant-stock │        │
│   │ -control    │    │ strategy    │    │ -list       │        │
│   │ (宏观风控)   │    │ -engine     │    │ (对外接口)   │        │
│   └─────────────┘    │ (策略引擎)   │    └─────────────┘        │
│                      └─────────────┘                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

**服务一：quant-data-collector（数据采集服务）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 从外部数据源采集行情、公告、财务等原始数据 |
| 拆分依据 | 数据采集是系统的**数据入口**，与下游业务逻辑解耦；采集频率高（秒级/分钟级），需独立扩缩容 |
| 独立部署诉求 | 交易时段需要高频采集，非交易时段可缩容降本 |
| 技术特点 | 高并发、IO密集、对外部API强依赖、需限流保护 |

**服务二：quant-data-archive（数据归档服务）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 接收所有微服务的日志/数据，统一归档到 ES / 数据仓库 |
| 拆分依据 | 这是**平台级基础设施能力**，与业务无关；所有服务多实例的日志都汇聚到此处理 |
| 独立部署诉求 | 日志量与业务量不成正比，需独立扩缩容；归档故障不应影响业务服务 |
| 技术特点 | Kafka消费者、批量写入、背压处理、高吞吐低延迟 |

**服务三：quant-risk-control（风控服务）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 宏观市场风控：大盘熔断预警、系统性风险识别、整体仓位控制 |
| 拆分依据 | 风控关注**宏观市场层面**，与策略引擎的**微观个股分析**是不同维度的关注点 |
| 独立部署诉求 | 风控规则变更频繁，需独立发布；风控服务故障时策略引擎应能降级运行 |
| 技术特点 | 实时计算、规则引擎、熔断器模式、多级预警 |

**服务四：quant-stock-list（股票列表服务）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 对外提供选股结果查询API，供客户端/第三方调用 |
| 拆分依据 | 这是**面向客户的对外接口层**，需要独立的限流、熔断、SLA保障 |
| 独立部署诉求 | 客户访问量与策略计算量不成正比，需独立扩缩容；对外接口稳定性要求更高 |
| 技术特点 | API网关特性、缓存优先、限流熔断、接口版本管理 |

**服务五：quant-strategy-engine（策略引擎服务）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 执行选股策略，进行微观个股分析（技术指标、量价分析等） |
| 拆分依据 | 策略引擎是**核心计算密集型服务**，CPU资源消耗大，需独立扩缩容 |
| 独立部署诉求 | 策略迭代频繁，需独立发布；计算任务可水平扩展 |
| 技术特点 | 计算密集型、责任链模式、策略插件化、异步并行计算 |

**服务六：quant-xxl-job（任务调度管理端）**

| 维度 | 说明 |
|------|------|
| 核心职责 | 分布式任务调度管理，提供任务配置、监控、日志查看等功能 |
| 拆分依据 | 这是**独立的第三方组件**（XXL-JOB），有自己的管理界面和运维需求 |
| 独立部署诉求 | 调度中心高可用，需独立部署多实例；与业务服务完全解耦 |
| 技术特点 | 调度中心、执行器分离、故障转移、任务分片 |

14.3 面试深挖应对（必背）

**Q1：为什么 risk-control 要独立，不能和 strategy-engine 放一起？**

> 回答要点：
> 1. **关注点不同**：风控关注宏观市场（大盘、系统性风险），策略关注微观个股（技术指标）
> 2. **变更频率不同**：风控规则可能因监管要求随时调整，策略迭代有自己的节奏
> 3. **故障隔离**：风控异常时，策略引擎可以降级运行（给出风险提示但不阻断）
> 4. **团队职责**：大厂里风控通常是独立团队，与策略团队分开

**Q2：为什么 stock-list 要独立，不能是 strategy-engine 的一个接口？**

> 回答要点：
> 1. **边界不同**：strategy-engine 是内部计算服务，stock-list 是对外接口层
> 2. **SLA不同**：对外接口需要更高的稳定性保障（99.9%+），独立后可针对性优化
> 3. **扩缩容策略不同**：客户访问量和策略计算量不成正比
> 4. **安全边界**：对外接口需要独立的认证、限流、审计

**Q3：为什么 data-archive 要独立，不能让各服务自己写ES？**

> 回答要点：
> 1. **统一管控**：日志格式、字段规范、索引策略统一管理
> 2. **解耦依赖**：业务服务不直接依赖ES，降低耦合度
> 3. **故障隔离**：ES故障不影响业务服务正常运行
> 4. **资源优化**：批量写入、背压控制，避免各服务各自为战

**Q4：这么多服务，运维成本怎么控制？**

> 回答要点：
> 1. **容器化部署**：Docker + K8s，统一编排
> 2. **统一配置中心**：Nacos 管理所有配置
> 3. **全链路追踪**：Skywalking / Zipkin，快速定位问题
> 4. **统一日志平台**：就是 data-archive + ES + Kibana

14.4 拆分决策检查清单

在决定是否拆分一个新服务时，必须回答以下问题：

- [ ] 该服务有独立的业务边界吗？能用一句话描述其职责吗？
- [ ] 该服务需要独立扩缩容吗？与其他服务的资源消耗模式不同吗？
- [ ] 该服务需要独立发布吗？变更频率与其他服务不同吗？
- [ ] 该服务故障时，其他服务能否降级运行？
- [ ] 拆分后，服务间调用复杂度是否可接受？

如果以上问题大部分回答"是"，则拆分合理；否则应考虑合并。
