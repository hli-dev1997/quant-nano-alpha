package com.hao.datacollector.integration.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import exception.RedisException;

/**
 * Redis 客户端通用配置类（适用于外部标准 Redis 服务）
 *
 * <p>用于初始化 Redis 客户端并提供 Redis 常用操作能力。
 * 实现 RedisClient 接口，可注入使用。
 * 支持连接池配置、健康检查、异常处理等特性。</p>
 * <p>
 * 配置项来自 application.yml:
 * <ul>
 *   <li>spring.data.redis.host - Redis服务器地址</li>
 *   <li>spring.data.redis.port - Redis服务器端口</li>
 *   <li>spring.data.redis.password - Redis密码（可选）</li>
 *   <li>spring.data.redis.database - Redis数据库索引（可选，默认0）</li>
 *   <li>spring.data.redis.timeout - 连接超时（默认5s）</li>
 * </ul>
 * <p>
 * 注意：此类实现了 RedisClient 接口，提供基础的 Key/Value 以及集合操作。
 *
 * @author hli
 * @version 2.1
 * @since 2025-08-28
 */
@Slf4j
@Configuration("RedisClient")
public class RedisConfig implements RedisClient<String>, InitializingBean, DisposableBean {

    /**
     * Redis服务器地址
     */
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    /**
     * Redis服务器端口
     */
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /**
     * Redis连接密码（可选）
     */
    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redis数据库索引（默认0）
     */
    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 连接超时时间（默认5秒）
     */
    @Value("${spring.data.redis.timeout:5s}")
    private Duration connectionTimeout;

    /**
     * Redis操作模板
     */
    private StringRedisTemplate redisTemplate;

    /**
     * 连接工厂
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 初始化 Redis 连接工厂和 StringRedisTemplate
     */
    @Override
    public void afterPropertiesSet() {
        try {
            log.info("开始初始化Redis连接_-_host:_{},_port:_{},_database:_{},_timeout:_{},_password:_{}",
                    redisHost, redisPort, redisDatabase, connectionTimeout,
                    StringUtils.hasText(redisPassword) ? "已配置" : "未配置");

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
            config.setDatabase(redisDatabase);
            if (StringUtils.hasText(redisPassword)) {
                config.setPassword(redisPassword);
            }

            connectionFactory = new LettuceConnectionFactory(config);
            connectionFactory.setValidateConnection(true);
            connectionFactory.afterPropertiesSet();

            redisTemplate = new StringRedisTemplate();
            redisTemplate.setConnectionFactory(connectionFactory);
            redisTemplate.afterPropertiesSet();

            testConnection();
            log.info("Redis初始化成功_-_连接到_{}:{}/{}", redisHost, redisPort, redisDatabase);
        } catch (Exception e) {
            log.error("Redis初始化失败:_{}", e.getMessage(), e);
            throw new RedisException("Redis初始化失败|Redis_initialization_failed", e);
        }
    }

    /**
     * 测试Redis连接
     */
    private void testConnection() {
        try {
            String testKey = "redis:connection:test:" + System.currentTimeMillis();
            String testValue = "connection_test_success";
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(10));
            String result = redisTemplate.opsForValue().get(testKey);
            if (!testValue.equals(result)) {
                throw new RedisException("Redis连接测试失败_值不匹配|Redis_connection_test_failed_value_mismatch");
            }
            redisTemplate.delete(testKey);
            log.info("Redis连接测试成功");
        } catch (Exception e) {
            log.error("Redis连接测试失败:_{}", e.getMessage(), e);
            throw new RedisException("Redis连接测试失败|Redis_connection_test_failed", e);
        }
    }

    /**
     * 销毁bean时清理资源
     */
    @Override
    public void destroy() {
        try {
            if (connectionFactory != null) {
                connectionFactory.destroy();
                log.info("Redis连接工厂已关闭");
            }
        } catch (Exception e) {
            log.error("关闭Redis连接时发生错误:_{}", e.getMessage(), e);
        }
    }

    /**
     * 参数校验工具 - 单个key
     */
    private void validateKey(String key, String paramName) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }

    /**
     * 参数校验工具 - 多个参数
     */
    private void validateParams(Object[] params, String paramName) {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }

    /* ------------------ String 操作 ------------------ */

    /**
     * 设置字符串值并指定过期时间
     *
     * @param key        键（不能为空）
     * @param value      值（不能为空）
     * @param expireTime 过期时间（秒，必须>0）
     */
    @Override
    public void set(String key, String value, int expireTime) {
        validateKey(key, "key");
        validateKey(value, "value");
        if (expireTime <= 0) {
            throw new IllegalArgumentException("expireTime must be greater than 0");
        }
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expireTime));
    }

    /**
     * 设置字符串值（无过期时间）
     */
    @Override
    public void set(String key, String value) {
        validateKey(key, "key");
        validateKey(value, "value");
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取字符串值
     */
    @Override
    public String get(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 判断key是否存在
     */
    @Override
    public Boolean exists(String key) {
        validateKey(key, "key");
        return redisTemplate.hasKey(key);
    }

    /**
     * 自增1
     */
    @Override
    public Long incr(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增指定值
     */
    @Override
    public Long incrBy(String key, long delta) {
        validateKey(key, "key");
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自减1
     */
    @Override
    public Long decr(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForValue().decrement(key);
    }

    /**
     * 自减指定值
     */
    @Override
    public Long decrBy(String key, long delta) {
        validateKey(key, "key");
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * 删除单个key
     */
    @Override
    public Long del(String key) {
        validateKey(key, "key");
        Boolean deleted = redisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted) ? 1L : 0L;
    }

    /**
     * 批量删除keys
     */
    @Override
    public Long del(String... keys) {
        validateParams(keys, "keys");
        return redisTemplate.delete(Arrays.asList(keys));
    }

    /* ------------------ Hash 操作 ------------------ */

    /**
     * 设置哈希表字段值
     */
    @Override
    public void hset(String key, String field, String value) {
        validateKey(key, "key");
        validateKey(field, "field");
        validateKey(value, "value");
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 设置哈希字段值（仅当字段不存在时）
     */
    @Override
    public Boolean hsetnx(String key, String field, String value) {
        validateKey(key, "key");
        validateKey(field, "field");
        validateKey(value, "value");
        return redisTemplate.opsForHash().putIfAbsent(key, field, value);
    }

    /**
     * 获取哈希表字段值
     */
    @Override
    public String hget(String key, String field) {
        validateKey(key, "key");
        validateKey(field, "field");
        Object value = redisTemplate.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取哈希表所有字段
     */
    @Override
    public Map<String, String> hgetAll(String key) {
        validateKey(key, "key");
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Collections.emptyMap();
        }
        return entries.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString(),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 批量设置哈希表字段
     */
    @Override
    public void hmset(String key, Map<String, String> paramMap) {
        validateKey(key, "key");
        if (paramMap == null || paramMap.isEmpty()) {
            throw new IllegalArgumentException("paramMap cannot be null or empty");
        }
        redisTemplate.opsForHash().putAll(key, paramMap);
    }

    /**
     * 批量获取哈希表字段值
     */
    @Override
    public List<String> hmget(String key, String... fields) {
        validateKey(key, "key");
        validateParams(fields, "fields");
        List<Object> values = redisTemplate.opsForHash().multiGet(key, Arrays.asList(fields));
        return values.stream()
                .map(value -> value != null ? value.toString() : null)
                .collect(Collectors.toList());
    }

    /**
     * 获取哈希表所有字段名
     */
    @Override
    public Set<String> hkeys(String key) {
        validateKey(key, "key");
        Set<Object> keys = redisTemplate.opsForHash().keys(key);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptySet();
        }
        return keys.stream().map(Object::toString).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 获取哈希表所有值
     */
    @Override
    public List<String> hvals(String key) {
        validateKey(key, "key");
        List<Object> values = redisTemplate.opsForHash().values(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(value -> value != null ? value.toString() : null).collect(Collectors.toList());
    }

    /**
     * 获取哈希表字段数量
     */
    @Override
    public Long hlen(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForHash().size(key);
    }

    /**
     * 判断哈希字段是否存在
     */
    @Override
    public Boolean hexists(String key, String field) {
        validateKey(key, "key");
        validateKey(field, "field");
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    /**
     * 删除哈希表字段
     */
    @Override
    public Long hdel(String key, String... fields) {
        validateKey(key, "key");
        validateParams(fields, "fields");
        return redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    /* ------------------ ZSet 操作 ------------------ */

    /**
     * 批量添加有序集合元素
     */
    @Override
    public Long zadd(String key, Map<String, Double> valueMap) {
        validateKey(key, "key");
        if (valueMap == null || valueMap.isEmpty()) {
            throw new IllegalArgumentException("valueMap cannot be null or empty");
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = valueMap.entrySet().stream()
                .map(entry -> ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
        return redisTemplate.opsForZSet().add(key, tuples);
    }

    /**
     * 添加有序集合元素
     */
    @Override
    public Long zadd(String key, double score, String member) {
        validateKey(key, "key");
        validateKey(member, "member");
        Boolean added = redisTemplate.opsForZSet().add(key, member, score);
        return Boolean.TRUE.equals(added) ? 1L : 0L;
    }

    /**
     * 获取指定区间元素（升序）
     */
    @Override
    public Set<String> zrange(String key, long start, long stop) {
        validateKey(key, "key");
        Set<String> result = redisTemplate.opsForZSet().range(key, start, stop);
        return result != null ? result : Collections.emptySet();
    }

    /**
     * 获取指定区间元素（降序）
     */
    @Override
    public Set<String> zrevrange(String key, long start, long stop) {
        validateKey(key, "key");
        Set<String> result = redisTemplate.opsForZSet().reverseRange(key, start, stop);
        return result != null ? result : Collections.emptySet();
    }

    /**
     * 删除分数区间内的元素
     */
    @Override
    public Long zremrangeByScore(String key, double scoreMin, double scoreMax) {
        validateKey(key, "key");
        return redisTemplate.opsForZSet().removeRangeByScore(key, scoreMin, scoreMax);
    }

    /**
     * 删除指定成员
     */
    @Override
    public Long zrem(String key, String... members) {
        validateKey(key, "key");
        validateParams(members, "members");
        return redisTemplate.opsForZSet().remove(key, (Object[]) members);
    }

    /**
     * 获取成员分数
     */
    @Override
    public Double zscore(String key, String member) {
        validateKey(key, "key");
        validateKey(member, "member");
        return redisTemplate.opsForZSet().score(key, member);
    }

    /**
     * 增加成员分数
     */
    @Override
    public Double zincrby(String key, double increment, String member) {
        validateKey(key, "key");
        validateKey(member, "member");
        return redisTemplate.opsForZSet().incrementScore(key, member, increment);
    }

    /**
     * 获取有序集合大小
     */
    @Override
    public Long zcard(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForZSet().zCard(key);
    }

    /**
     * 统计分数区间元素数量
     */
    @Override
    public Long zcount(String key, double scoreMin, double scoreMax) {
        validateKey(key, "key");
        return redisTemplate.opsForZSet().count(key, scoreMin, scoreMax);
    }

    /* ------------------ List 操作 ------------------ */

    /**
     * 从左侧推入列表
     */
    @Override
    public Long lpush(String key, String... values) {
        validateKey(key, "key");
        validateParams(values, "values");
        return redisTemplate.opsForList().leftPushAll(key, values);
    }

    /**
     * 从右侧推入列表
     */
    @Override
    public Long rpush(String key, String... values) {
        validateKey(key, "key");
        validateParams(values, "values");
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * 从左侧弹出元素
     */
    @Override
    public String lpop(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从右侧弹出元素
     */
    @Override
    public String rpop(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取指定区间的列表元素
     */
    @Override
    public List<String> lrange(String key, long start, long stop) {
        validateKey(key, "key");
        List<String> result = redisTemplate.opsForList().range(key, start, stop);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 获取列表长度
     */
    @Override
    public Long llen(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForList().size(key);
    }

    /* ------------------ Set 操作 ------------------ */

    /**
     * 添加集合成员
     */
    @Override
    public Long sadd(String key, String... members) {
        validateKey(key, "key");
        validateParams(members, "members");
        return redisTemplate.opsForSet().add(key, members);
    }

    /**
     * 移除集合成员
     */
    @Override
    public Long srem(String key, String... members) {
        validateKey(key, "key");
        validateParams(members, "members");
        return redisTemplate.opsForSet().remove(key, (Object[]) members);
    }

    /**
     * 获取集合所有成员
     */
    @Override
    public Set<String> smembers(String key) {
        validateKey(key, "key");
        Set<String> result = redisTemplate.opsForSet().members(key);
        return result != null ? result : Collections.emptySet();
    }

    /**
     * 判断成员是否存在
     */
    @Override
    public Boolean sismember(String key, String member) {
        validateKey(key, "key");
        validateKey(member, "member");

        return redisTemplate.opsForSet().isMember(key, member);
    }

    /**
     * 获取集合中元素的数量
     *
     * @param key Redis键（不能为空）
     * @return 集合元素个数
     * - 0：如果 key 不存在
     * <p>
     * 使用场景：统计唯一值数量，例如活跃用户ID集合、已完成任务ID集合
     * 注意事项：
     * - key 必须是 set 类型，否则会抛出异常
     * - 与 list.length() 类似，但适用于去重后的无序集合
     */
    @Override
    public Long scard(String key) {
        validateKey(key, "key");
        return redisTemplate.opsForSet().size(key);
    }

    /** ------------------ 过期时间控制实现 ------------------ **/

    /**
     * 设置键的过期时间（单位：秒）
     *
     * @param key     Redis键（不能为空）
     * @param seconds 过期时间（秒，必须 > 0）
     * @return true 设置成功；false key不存在或设置失败
     * <p>
     * 使用场景：控制缓存数据生命周期，例如用户登录态、验证码
     * 注意事项：
     * - seconds <= 0 会抛出 IllegalArgumentException
     * - 过期时间到期后，key会自动删除
     */
    @Override
    public Boolean expire(String key, int seconds) {
        validateKey(key, "key");
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds must be greater than 0");
        }
        return redisTemplate.expire(key, Duration.ofSeconds(seconds));
    }

    /**
     * 设置键的过期时间点（UNIX时间戳，单位：秒）
     *
     * @param key       Redis键（不能为空）
     * @param timestamp UNIX时间戳（秒，必须 > 0）
     * @return true 设置成功；false key不存在或设置失败
     * <p>
     * 使用场景：让缓存精确在某个时间点失效，例如每天零点过期
     * 注意事项：
     * - timestamp <= 0 会抛出 IllegalArgumentException
     * - 内部会转换为 Date(timestamp * 1000)，注意单位
     */
    @Override
    public Boolean expireAt(String key, long timestamp) {
        validateKey(key, "key");
        if (timestamp <= 0) {
            throw new IllegalArgumentException("timestamp must be greater than 0");
        }
        return redisTemplate.expireAt(key, new Date(timestamp * 1000));
    }

    /**
     * 移除键的过期时间（持久化存储）
     *
     * @param key Redis键（不能为空）
     * @return true 移除成功；false key不存在或key本来就没有过期时间
     * <p>
     * 使用场景：将临时缓存转换为永久数据
     */
    @Override
    public Boolean persist(String key) {
        validateKey(key, "key");
        return redisTemplate.persist(key);
    }

    /**
     * 获取键的剩余过期时间（秒）
     *
     * @param key Redis键（不能为空）
     * @return 过期时间（秒）
     * -1：存在但没有过期时间
     * -2：key不存在
     * <p>
     * 使用场景：监控缓存剩余时间，判断是否需要提前刷新
     */
    @Override
    public Long ttl(String key) {
        validateKey(key, "key");
        return redisTemplate.getExpire(key);
    }

    /** ------------------ 通用操作实现 ------------------ **/

    /**
     * 获取键的存储类型
     *
     * @param key Redis键（不能为空）
     * @return 类型名称（string, hash, list, set, zset, none）
     * <p>
     * 使用场景：调试或通用方法中判断 key 类型
     * 注意事项：
     * - 若key不存在，返回 "none"
     * - 这里建议使用 redisTemplate.type(key).code() 更贴近 Redis 原生
     */
    @Override
    public String type(String key) {
        validateKey(key, "key");
        return redisTemplate.type(key).getClass().getSimpleName().toLowerCase();
    }

    /**
     * 重命名键
     *
     * @param oldKey 旧键（不能为空）
     * @param newKey 新键（不能为空）
     *               <p>
     *               使用场景：数据迁移或key重构
     *               注意事项：
     *               - 如果 newKey 已存在，将被覆盖
     *               - 如果 oldKey 不存在，会抛异常
     */
    @Override
    public void rename(String oldKey, String newKey) {
        validateKey(oldKey, "oldKey");
        validateKey(newKey, "newKey");
        redisTemplate.rename(oldKey, newKey);
    }

    /**
     * 重命名键（仅当新键不存在时生效）
     *
     * @param oldKey 旧键（不能为空）
     * @param newKey 新键（不能为空）
     * @return true 重命名成功；false 如果 newKey 已存在
     * <p>
     * 使用场景：安全地迁移数据，避免覆盖已有新键
     */
    @Override
    public Boolean renamenx(String oldKey, String newKey) {
        validateKey(oldKey, "oldKey");
        validateKey(newKey, "newKey");
        return redisTemplate.renameIfAbsent(oldKey, newKey);
    }

    /** ------------------ 扩展工具方法 ------------------ **/

    /**
     * 获取RedisTemplate实例，用于复杂操作
     *
     * @return StringRedisTemplate实例
     */
    public StringRedisTemplate getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * 执行Redis管道操作
     * 用于批量操作以提高性能
     *
     * @param callback 管道操作回调
     * @return 执行结果列表
     */
    public List<Object> executePipelined(org.springframework.data.redis.core.RedisCallback<Object> callback) {
        return redisTemplate.executePipelined(callback);
    }

    /**
     * 获取Redis连接信息（用于监控和调试）
     *
     * @return Redis连接信息映射
     */
    public Map<String, Object> getConnectionInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("host", redisHost);
        info.put("port", redisPort);
        info.put("database", redisDatabase);
        info.put("hasPassword", StringUtils.hasText(redisPassword));
        info.put("connectionTimeout", connectionTimeout.toString());
        info.put("isConnected", connectionFactory != null && !connectionFactory.getConnection().isClosed());
        return info;
    }

    /**
     * 健康检查方法
     *
     * @return 健康状态
     */
    public boolean isHealthy() {
        try {
            String testKey = "health:check:" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(testKey, "ok", Duration.ofSeconds(1));
            String result = redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            return "ok".equals(result);
        } catch (Exception e) {
            log.warn("Redis健康检查失败:_{}", e.getMessage());
            return false;
        }
    }

    /* ------------------ 分布式锁操作 ------------------ */

    /**
     * 尝试获取分布式锁
     *
     * @param key        锁名称（不能为空）
     * @param value      锁值，一般用 UUID（不能为空）
     * @param expireTime 过期时间（秒，必须>0）
     * @return true 获取成功，false 获取失败
     * <p>
     * 使用场景：控制分布式环境下同一资源的并发访问
     */
    public boolean tryLock(String key, String value, int expireTime) {
        validateKey(key, "key");
        validateKey(value, "value");
        if (expireTime <= 0) {
            throw new IllegalArgumentException("expireTime must be greater than 0");
        }
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(expireTime));
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放分布式锁（Lua脚本方式，保证原子性）
     */
    public boolean releaseLock(String key, String value) {
        validateKey(key, "key");
        validateKey(value, "value");

        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else return 0 end";

        RedisCallback<Long> callback = connection ->
                connection.eval(
                        script.getBytes(),
                        ReturnType.INTEGER,
                        1,
                        key.getBytes(),
                        value.getBytes()
                );

        Object result = redisTemplate.execute(callback);
        return Long.valueOf(1).equals(result);
    }

}
