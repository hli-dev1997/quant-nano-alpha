# 🚀 quant-nano-alpha 万级并发优化清单

> 基于全项目扫描的优化建议，目标：支撑万级 QPS 并发

---

## 一、当前项目状态

### ✅ 已具备能力

| 项目 | 状态 | 位置 |
|------|------|------|
| 虚拟线程 | ✅ 已启用 | data-collector / strategy-engine |
| 线程池配置 | ✅ 完善 | `ThreadPoolConfig.java` |
| 单元测试 | ✅ 44 个测试类 | 覆盖主要服务 |
| 目录规范 | ✅ gemini.md | 统一分层结构 |
| 日志规范 | ✅ 双语 | 中英文格式 |

### ⚠️ 待优化项

| 项目 | 状态 | 风险等级 |
|------|------|----------|
| 连接池配置 | ❌ 使用默认值 | 🔴 高 |
| 监控体系 | ❌ 无 Prometheus | 🔴 高 |
| 缓存使用 | ⚠️ 仅 1 处 | 🟡 中 |
| 事务管理 | ❌ 无 @Transactional | 🟡 中 |
| 链路追踪 | ❌ 无 SkyWalking | 🟡 中 |

---

## 二、P0 优化项（必做）

### 2.1 HikariCP 连接池配置

**影响服务**：所有需要 MySQL 的服务

**配置位置**：各服务 `application-dev.yml`

```yaml
spring:
  datasource:
    hikari:
      # 连接池大小（万级并发建议 50+）
      maximum-pool-size: 50
      minimum-idle: 10
      # 连接超时（毫秒）
      connection-timeout: 3000
      # 空闲超时（毫秒）
      idle-timeout: 60000
      # 连接最大生命周期
      max-lifetime: 1800000
      # 连接验证超时
      validation-timeout: 3000
      # 连接测试查询
      connection-test-query: SELECT 1
```

**面试要点**：
- 为什么不能用默认值？（默认 10 连接，高并发会阻塞）
- maximum-pool-size 怎么算？（CPU 核数 * 2 + 磁盘数，经验值 50-100）

---

### 2.2 Redis Lettuce 连接池配置

**配置位置**：各服务 `application-dev.yml`

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          # 最大活跃连接
          max-active: 100
          # 最大空闲连接
          max-idle: 50
          # 最小空闲连接
          min-idle: 10
          # 获取连接最大等待时间
          max-wait: 1000ms
        # 关闭超时
        shutdown-timeout: 2000ms
```

**面试要点**：
- Lettuce vs Jedis？（Lettuce 线程安全，基于 Netty）
- 连接池为什么重要？（避免频繁创建/销毁连接开销）

---

### 2.3 Prometheus 监控集成

**步骤 1**：添加依赖

在 `services/pom.xml` 添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**步骤 2**：配置暴露端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}
```

**步骤 3**：访问验证

```bash
curl http://localhost:8801/data-collector/actuator/prometheus
```

**面试要点**：
- 监控哪些指标？（QPS、P99 延迟、JVM 内存、线程池）
- 如何发现瓶颈？（Grafana 大盘 + 告警规则）

---

## 三、P1 优化项（建议做）

### 3.1 缓存层完善

**当前问题**：仅 `BaseDataServiceImpl` 使用了 `@Cacheable`

**建议添加缓存的位置**：

| 服务 | 方法 | 缓存策略 |
|------|------|----------|
| data-collector | 查询股票基础信息 | 1小时 TTL |
| strategy-engine | 获取策略配置 | 5分钟 TTL + 逻辑过期 |
| stock-list | 查询每日精选 | 参考 HIGH_CONCURRENCY_INTEGRATION.md |

### 3.2 事务管理

**建议添加 @Transactional 的位置**：

```java
// 涉及多表写入的服务方法
@Transactional(rollbackFor = Exception.class)
public void saveBatchData(...) {
    // 多表操作
}
```

**注意**：
- 事务内禁止 Redis/HTTP 调用（防止长事务）
- 只读方法用 `@Transactional(readOnly = true)`

---

## 四、P2 优化项（锦上添花）

### 4.1 链路追踪 (SkyWalking)

```xml
<!-- Agent 方式接入，无需代码改动 -->
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=quant-data-collector
-Dskywalking.collector.backend_service=localhost:11800
```

### 4.2 压测脚本

```bash
# wrk 压测脚本
wrk -t10 -c100 -d60s \
  -s post.lua \
  http://localhost:8801/data-collector/api/quotation/query
```

---

## 五、优化执行计划

### 第一周：基础设施

- [ ] 所有服务添加 HikariCP 配置
- [ ] 所有服务添加 Lettuce 连接池配置
- [ ] 集成 Prometheus + Grafana

### 第二周：缓存优化

- [ ] stock-list 实现多级缓存
- [ ] strategy-engine 策略配置缓存
- [ ] 热点数据预热机制

### 第三周：验证与调优

- [ ] 编写压测脚本
- [ ] 执行 1000/5000/10000 QPS 压测
- [ ] 输出压测报告
- [ ] 根据瓶颈调优

---

## 六、检查清单

在声称"支持万级并发"之前，确保：

- [ ] HikariCP 连接池已配置（50+ 连接）
- [ ] Redis 连接池已配置（100+ 连接）
- [ ] Prometheus 监控已接入
- [ ] 至少一次 10000 QPS 压测通过
- [ ] 有压测报告和调优记录
- [ ] 能讲清楚每个组件的原理

---

**文档版本**：v1.0  
**创建时间**：2026-01-19  
**维护者**：AI Assistant
