package com.hao.datacollector.service.job;

import com.hao.datacollector.service.IndexPreCloseCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 指数昨收价缓存预热任务
 * <p>
 * 在服务启动时自动预热各指数的昨收价缓存到 Redis。
 * 使用 {@link Order} 注解确保在 DateCache 初始化之后执行。
 *
 * @author hli
 * @date 2026-01-18
 */
@Slf4j
@Component
@Order(100) // 确保在 DateCache 等基础组件初始化之后执行
@RequiredArgsConstructor
public class IndexPreCloseWarmUpRunner implements CommandLineRunner {

    private final IndexPreCloseCacheService indexPreCloseCacheService;

    @Override
    public void run(String... args) {
        log.info("========== 指数昨收价缓存预热开始|Index_pre_close_cache_warm_up_start ==========");

        try {
            int cachedCount = indexPreCloseCacheService.warmUpCache();
            log.info("========== 指数昨收价缓存预热完成|Index_pre_close_cache_warm_up_done,count={} ==========", cachedCount);
        } catch (Exception e) {
            // 预热失败不影响服务启动，仅记录错误日志
            log.error("指数昨收价缓存预热失败|Index_pre_close_cache_warm_up_error", e);
        }
    }
}
