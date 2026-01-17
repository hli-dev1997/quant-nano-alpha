package com.hao.quant.stocklist.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 封装多级缓存读写逻辑。
 * <p>
 * 对外提供 L1 (Caffeine) + L2 (Redis) 的访问方法,便于领域服务复用。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StablePicksCacheRepository {

    private final Cache<String, CacheWrapper<?>> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;

    @SuppressWarnings("unchecked")
    /**
     * 从本地 Caffeine 缓存读取。
     */
    public <T> Optional<CacheWrapper<T>> getLocal(String cacheKey) {
        return Optional.ofNullable((CacheWrapper<T>) caffeineCache.getIfPresent(cacheKey));
    }

    @SuppressWarnings("unchecked")
    /**
     * 从 Redis 缓存读取。
     */
    public <T> Optional<CacheWrapper<T>> getDistributed(String cacheKey) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of((CacheWrapper<T>) value);
        } catch (ClassCastException ex) {
            log.warn("缓存类型不匹配|Cache_type_mismatch,cacheKey={}", cacheKey);
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    /**
     * 写入本地与 Redis 缓存。
     */
    public void put(String cacheKey, CacheWrapper<?> wrapper, Duration redisTtl) {
        caffeineCache.put(cacheKey, wrapper);
        redisTemplate.opsForValue().set(cacheKey, wrapper, redisTtl);
    }

    /**
     * 仅更新本地缓存。
     */
    public void putLocal(String cacheKey, CacheWrapper<?> wrapper) {
        caffeineCache.put(cacheKey, wrapper);
    }

    /**
     * 同步移除本地与 Redis 缓存。
     */
    public void evict(String cacheKey) {
        caffeineCache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
    }

    /**
     * 将缓存 Key 记录到指定的 Set 集合中，用于后续批量清理。
     *
     * @param groupKey 集合 Key (如按日期分组)
     * @param cacheKey 具体的缓存 Key
     * @param ttl      集合的过期时间 (通常应大于缓存内容的过期时间)
     */
    public void trackKeyInGroup(String groupKey, String cacheKey, Duration ttl) {
        redisTemplate.opsForValue().getOperations().boundSetOps(groupKey).add(cacheKey);
        redisTemplate.expire(groupKey, ttl);
    }

    /**
     * 清理指定分组下的所有缓存 Key。
     *
     * @param groupKey 集合 Key
     */
    public void clearGroup(String groupKey) {
        // 1. 获取该分组下的所有 Key
        java.util.Set<Object> keys = redisTemplate.opsForSet().members(groupKey);
        if (keys != null && !keys.isEmpty()) {
            // 2. 逐个清理 (本地 + Redis)
            keys.forEach(key -> evict(key.toString()));
            log.info("分组缓存清理完成|Group_cache_cleared,groupKey={},count={}", groupKey, keys.size());
        }
        // 3. 删除分组集合本身
        redisTemplate.delete(groupKey);
    }
}
