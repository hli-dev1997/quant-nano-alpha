# 📚 quant-stock-list 高并发能力接入指南

> 本文档记录如何将 `RedisStudy` 项目中验证过的高并发技术逐步接入到 `quant-stock-list` 模块。

---

## 一、当前模块状态

### 1.1 目录结构

```
src/main/java/com/hao/quant/stocklist/
├── StockListApplication.java     # 应用入口
├── controller/                   # 接口层
│   ├── StablePicksController.java
│   └── vo/StablePicksVO.java
├── config/                       # 配置类
│   ├── KafkaConfig.java
│   ├── MyBatisConfig.java
│   ├── RedisConfig.java
│   └── SwaggerConfig.java
└── common/dto/                   # 公共 DTO
    ├── PageResult.java
    └── Result.java
```

### 1.2 待完善能力

| 能力 | 当前状态 | 触发条件 | 来源 |
|------|----------|----------|------|
| 基础查询 | ✅ 框架已有 | - | 本模块 |
| Redis 缓存 | ⏳ 待接入 | 日访问量 > 1万 | RedisStudy |
| 本地缓存 | ⏳ 待接入 | QPS > 500 | RedisStudy |
| 布隆过滤器 | ⏳ 待接入 | 需防穿透时 | RedisStudy |
| 分布式锁 | ⏳ 待接入 | 需防击穿时 | RedisStudy |
| 限流熔断 | ⏳ 待接入 | 秒级万并发时 | RedisStudy |

---

## 二、技术来源：RedisStudy 项目

**路径**：`E:\project\RedisStudy`

### 2.1 可复用组件清单

| 组件 | 文件路径 | 核心能力 |
|------|----------|----------|
| **布隆过滤器** | `common/util/BloomFilterUtil.java` | 基于 Redis Bitmap，防缓存穿透 |
| **缓存击穿防护** | `common/util/CacheBreakdownUtil.java` | 逻辑过期 + 互斥锁 |
| **分布式限流** | `common/util/RedisRateLimiter.java` | Lua 脚本滑动窗口 |
| **分布式锁** | `integration/lock/RedisDistributedLock.java` | SET NX EX + 看门狗续期 |
| **Redis 客户端** | `integration/redis/RedisClientImpl.java` | 封装 101 个 Redis 命令 |
| **逻辑过期封装** | `common/model/RedisLogicalData.java` | 逻辑过期时间封装 |
| **线程池配置** | `config/ThreadPoolConfig.java` | IO 密集型异步线程池 |

---

## 三、渐进式接入计划

### 阶段 1：基础 Redis 缓存（日访问量 > 1万）

#### 3.1.1 引入依赖

`pom.xml` 当前已包含：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

#### 3.1.2 新增文件

```
src/main/java/com/hao/quant/stocklist/
├── service/
│   ├── StablePicksService.java           # 服务接口
│   └── impl/
│       └── StablePicksServiceImpl.java   # 服务实现（带缓存）
└── integration/
    └── redis/
        └── StablePicksCacheRepository.java  # 缓存仓库
```

#### 3.1.3 核心代码示例

**StablePicksServiceImpl.java**
```java
@Service
@Slf4j
public class StablePicksServiceImpl implements StablePicksService {

    private final StablePicksCacheRepository cacheRepository;
    // ... 其他依赖

    @Override
    public PageResult<StablePicksVO> queryDailyPicks(LocalDate tradeDate, int pageNum, int pageSize) {
        // 1. 构建缓存 Key
        String cacheKey = "stock:picks:" + tradeDate + ":" + pageNum + ":" + pageSize;
        
        // 2. 查询缓存
        PageResult<StablePicksVO> cached = cacheRepository.get(cacheKey);
        if (cached != null) {
            log.info("缓存命中|Cache_hit,key={}", cacheKey);
            return cached;
        }
        
        // 3. 缓存未命中，查询数据库
        log.info("缓存未命中_查询数据库|Cache_miss_query_db,key={}", cacheKey);
        PageResult<StablePicksVO> result = queryFromDatabase(tradeDate, pageNum, pageSize);
        
        // 4. 写入缓存（随机 TTL 防雪崩）
        cacheRepository.setWithRandomTtl(cacheKey, result, 1, TimeUnit.HOURS);
        
        return result;
    }
}
```

---

### 阶段 2：本地缓存 Caffeine（QPS > 500）

#### 3.2.1 新增依赖

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

#### 3.2.2 从 RedisStudy 复制

- `config/CacheConfig.java` → 本地缓存配置

#### 3.2.3 核心代码示例

```java
@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)           // 最大条目数
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后 5 分钟过期
                .recordStats()                 // 开启统计（可选）
                .build();
    }
}
```

**多级缓存查询流程**：
```
请求 → L1 本地缓存 (Caffeine) 
           ↓ 未命中
      L2 分布式缓存 (Redis)
           ↓ 未命中
      数据库 (MySQL)
           ↓
      回填 L2 → 回填 L1 → 返回
```

---

### 阶段 3：布隆过滤器（防缓存穿透）

#### 3.3.1 适用场景

当查询不存在的交易日期或股票代码时，防止恶意请求直接打到数据库。

#### 3.3.2 从 RedisStudy 复制

- `common/util/BloomFilterUtil.java`

#### 3.3.3 接入方式

```java
@Service
public class StablePicksServiceImpl {

    @PostConstruct
    public void initBloomFilter() {
        // 启动时加载所有有效交易日期到布隆过滤器
        List<LocalDate> validDates = tradeDateRepository.findAllValidDates();
        validDates.forEach(date -> bloomFilter.add(date.toString()));
        log.info("布隆过滤器初始化完成|Bloom_filter_init,count={}", validDates.size());
    }

    public PageResult<StablePicksVO> queryDailyPicks(LocalDate tradeDate, ...) {
        // 1. 布隆过滤器前置拦截
        if (!bloomFilter.mightContain(tradeDate.toString())) {
            log.warn("无效日期请求_布隆过滤器拦截|Invalid_date_blocked,date={}", tradeDate);
            return PageResult.empty();
        }
        
        // 2. 正常缓存查询流程...
    }
}
```

---

### 阶段 4：分布式锁（防缓存击穿）

#### 3.4.1 适用场景

热点数据（如某只热门股票）缓存失效瞬间，大量请求同时打到数据库。

#### 3.4.2 从 RedisStudy 复制

- `integration/lock/DistributedLock.java`（接口）
- `integration/lock/RedisDistributedLock.java`（实现）
- `integration/lock/RedisDistributedLockService.java`（工厂）

#### 3.4.3 接入方式

```java
public PageResult<StablePicksVO> queryDailyPicks(LocalDate tradeDate, ...) {
    String cacheKey = buildCacheKey(tradeDate, pageNum, pageSize);
    
    // 1. 查 L1 + L2 缓存
    PageResult<StablePicksVO> cached = getFromCache(cacheKey);
    if (cached != null) return cached;
    
    // 2. 缓存未命中，尝试获取分布式锁
    String lockKey = "lock:stock:picks:" + tradeDate;
    DistributedLock lock = lockService.getLock(lockKey);
    
    try {
        if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
            // 3. Double Check
            cached = getFromCache(cacheKey);
            if (cached != null) return cached;
            
            // 4. 查库并回填缓存
            PageResult<StablePicksVO> result = queryFromDatabase(...);
            saveToCache(cacheKey, result);
            return result;
        } else {
            // 5. 获取锁失败，返回降级数据或等待重试
            log.warn("获取锁失败_返回降级数据|Lock_failed_fallback,key={}", lockKey);
            return PageResult.empty();
        }
    } finally {
        lock.unlock();
    }
}
```

---

### 阶段 5：分布式限流（秒级万并发）

#### 3.5.1 从 RedisStudy 复制

- `common/util/RedisRateLimiter.java`
- `common/aspect/SimpleRateLimitAspect.java`
- `filters/GlobalRateLimitFilter.java`

#### 3.5.2 接入方式

**方式一：注解式限流**
```java
@GetMapping("/daily")
@RateLimit(qps = 1000, fallback = "rateLimitFallback")
public Result<PageResult<StablePicksVO>> queryDailyPicks(...) {
    // ...
}

public Result<PageResult<StablePicksVO>> rateLimitFallback(...) {
    return Result.failure(429, "请求过于频繁，请稍后再试");
}
```

**方式二：Filter 全局限流**
```java
@Component
public class StockListRateLimitFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ...) {
        if (!rateLimiter.tryAcquire("stock-list-api")) {
            response.setStatus(429);
            response.getWriter().write("Too Many Requests");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

---

## 四、配置参考

### 4.1 application.yml 扩展配置

```yaml
# 缓存配置
cache:
  stock-picks:
    ttl: 3600          # 缓存 TTL（秒）
    random-range: 360  # 随机范围（防雪崩）

# 布隆过滤器配置
bloom-filter:
  bit-size: 16777216   # 2^24 = 16M 位
  hash-count: 3        # 哈希函数数量

# 分布式锁配置
distributed-lock:
  watchdog:
    timeout: 30000     # 看门狗超时（毫秒）
    
# 限流配置
rate-limit:
  stock-list-api:
    qps: 1000          # 每秒请求数上限
    fallback-ratio: 0.5  # Redis 故障时降级比例
```

---

## 五、演进检查清单

在决定引入某项能力时，请确认：

- [ ] **业务需要**：当前 QPS / DAU 是否真的需要该能力？
- [ ] **原理理解**：能否向面试官讲清楚该技术的原理和权衡？
- [ ] **验证通过**：是否在 RedisStudy 中充分测试过？
- [ ] **监控就绪**：是否有对应的监控指标和告警？
- [ ] **降级方案**：该组件故障时的降级策略是什么？

---

## 七、万级并发容量分析

### 7.1 理论吞吐量验证

按 RedisStudy 基准测试数据：

| 组件 | 单机 QPS | 说明 |
|------|----------|------|
| Caffeine L1 | 100,000+ | 本地缓存，几乎无延迟 |
| Redis GET | 50,000+ | 热搜榜查询实测 |
| MySQL 查询 | 1,000~3,000 | 取决于索引和连接池 |

**多级缓存命中后的请求分布**：
```
假设 10000 QPS 请求：
├── L1 命中率 80% → 8000 QPS → Caffeine (100K 上限，轻松承接)
├── L2 命中率 15% → 1500 QPS → Redis (50K 上限，轻松承接)
└── 穿透到 DB 5%  → 500 QPS  → MySQL (需连接池 50+ 才稳)

结论：✅ 合理配置后可支撑万级并发
```

### 7.2 真正的瓶颈清单

万级并发不只是代码问题，还需要：

| 层面 | 要求 | 检查项 |
|------|------|--------|
| **Redis 集群** | 3主3从起步，读写分离 | `☐ 已部署集群` |
| **MySQL 连接池** | 50~100 连接，合理索引 | `☐ HikariCP 配置` |
| **JVM 调优** | 堆内存 4G+，G1/ZGC | `☐ -Xms4g -Xmx4g` |
| **线程池** | IO 密集型：核心数 * 2~4 | `☐ ThreadPoolConfig` |
| **网络带宽** | 100Mbps+ | `☐ 部署环境检查` |
| **容器资源** | 4核8G 起步 | `☐ Docker/K8s 配置` |

### 7.3 面试官追问清单

**Q：你说能撑万级并发，怎么验证的？**

需要准备的素材：

1. **压测报告**：用 JMeter / wrk / Gatling 跑过真实压测
2. **监控数据**：Prometheus + Grafana 看 P99 延迟
3. **瓶颈分析**：知道先挂的是 Redis 还是 MySQL 还是 CPU
4. **调优过程**：记录每次优化的前后对比

---

## 八、压测验证计划

### 8.1 阶段性压测路径

```
阶段 1：完成功能开发 + 单元测试
    ↓
阶段 2：本地压测 1000 QPS（验证基础架构）
    ↓
阶段 3：本地压测 5000 QPS（发现第一批瓶颈）
    ↓
阶段 4：优化瓶颈 + 重测
    ↓
阶段 5：Docker 环境压测 10000 QPS（模拟生产）
    ↓
阶段 6：输出压测报告（面试素材）
```

### 8.2 压测工具推荐

| 工具 | 特点 | 适用场景 |
|------|------|----------|
| **wrk** | 轻量、高性能 | 快速验证 QPS 上限 |
| **JMeter** | GUI、功能全 | 复杂场景、团队协作 |
| **Gatling** | 代码化、报告美观 | CI/CD 集成 |
| **ab** | 简单易用 | 快速冒烟测试 |

### 8.3 压测命令示例

**wrk 示例**：
```bash
# 10 线程，100 并发，持续 30 秒
wrk -t10 -c100 -d30s http://localhost:8806/quant-stock-list/api/v1/stable-picks/daily?tradeDate=2026-01-19
```

**JMeter 配置要点**：
- 线程组：100~1000 用户
- Ramp-Up：10~30 秒
- 持续时间：60~300 秒
- 聚合报告：关注 P99、错误率

### 8.4 压测报告模板

```markdown
## 压测报告：quant-stock-list /daily 接口

### 测试环境
- 机器配置：4C8G
- Redis：3主3从集群
- MySQL：单机，50 连接池
- JVM：-Xms4g -Xmx4g -XX:+UseG1GC

### 测试结果

| 并发数 | QPS | 平均延迟 | P99 延迟 | 错误率 |
|--------|-----|----------|----------|--------|
| 100 | 5,200 | 18ms | 45ms | 0% |
| 500 | 9,800 | 48ms | 120ms | 0.1% |
| 1000 | 10,500 | 92ms | 280ms | 0.5% |

### 瓶颈分析
- 500 并发时 Redis 连接池告警
- 1000 并发时 MySQL 连接池饱和

### 优化建议
1. Redis 连接池从 50 扩展到 100
2. MySQL 开启慢查询日志，优化 SQL
```

---

## 九、相关资源

| 资源 | 路径 |
|------|------|
| RedisStudy 项目 | `E:\project\RedisStudy` |
| 布隆过滤器误判方案 | `E:\project\RedisStudy\BloomFilter_FalsePositive_Solution.md` |
| 限流架构设计 | `E:\project\RedisStudy\RateLimiting_Architecture_Notes.md` |
| 项目规范 | `E:\project\quant-nano-alpha\gemini.md` |

---

**文档版本**：v1.1  
**创建时间**：2026-01-19  
**更新时间**：2026-01-19  
**维护者**：AI Assistant
