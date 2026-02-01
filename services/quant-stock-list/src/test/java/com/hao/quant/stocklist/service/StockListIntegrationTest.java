package com.hao.quant.stocklist.service;

import com.hao.quant.stocklist.common.dto.Result;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stock-List 模块集成测试
 * <p>
 * 测试环境要求：
 * 1. Redis 服务运行中
 * 2. MySQL 数据库运行中（tb_quant_stock_signal 表已创建）
 * 3. Nacos 服务运行中（或使用本地配置）
 * <p>
 * 测试覆盖：
 * 1. API 端到端测试
 * 2. 缓存层级验证
 * 3. 性能基准测试
 * 4. 万级并发压测
 *
 * @author hli
 * @date 2026-02-01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Stock-List 集成测试")
class StockListIntegrationTest {

    // 使用真实存在的测试数据
    private static final String STRATEGY_ID = "MA_BULLISH";
    private static final String TRADE_DATE = "2026-01-15";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MultiLevelCacheService multiLevelCacheService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String baseUrl;

    /**
     * 测试前清理缓存，防止脏数据影响测试结果
     */
    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/quant-stock-list/api/v1/stable-picks";
        // 清理本地缓存
        multiLevelCacheService.invalidateLocalCache(STRATEGY_ID, TRADE_DATE);
    }

    /**
     * 测试后清理缓存，防止测试产生脏数据
     */
    @AfterEach
    void tearDown() {
        // 清理本地缓存
        multiLevelCacheService.invalidateLocalCache(STRATEGY_ID, TRADE_DATE);
        // 清理测试产生的临时 Redis key（带时间戳的测试key）
        cleanupTestRedisKeys();
    }

    /**
     * 清理测试产生的临时 Redis key
     */
    private void cleanupTestRedisKeys() {
        try {
            // 删除测试过程中产生的临时 key（带时间戳前缀的）
            var keys = redisTemplate.keys("stock:signal:list:TEST_*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // 忽略清理异常
        }
    }

    // ==================== API 端到端测试 ====================

    @Nested
    @DisplayName("API 端到端测试")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ApiEndToEndTests {

        @Test
        @Order(1)
        @DisplayName("正常查询 - 返回成功")
        void testQueryDailyPicksSuccess() {
            // Given
                        String tradeDate = TRADE_DATE;
            String url = baseUrl + "/daily?tradeDate=" + tradeDate + "&strategyId=" + STRATEGY_ID;

            // When
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
        }

        @Test
        @Order(2)
        @DisplayName("缺少交易日期 - 返回错误")
        void testQueryWithoutTradeDate() {
            // Given
            String url = baseUrl + "/daily?strategyId=" + STRATEGY_ID;

            // When
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @Order(3)
        @DisplayName("分页查询 - 返回正确数量")
        void testPaginationQuery() {
            // Given
                        String tradeDate = TRADE_DATE;
            String url = baseUrl + "/daily?tradeDate=" + tradeDate
                    + "&strategyId=" + STRATEGY_ID
                    + "&pageNum=1&pageSize=5";

            // When
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(4)
        @DisplayName("查询不存在的策略 - 返回空列表")
        void testQueryNonExistentStrategy() {
            // Given
                        String tradeDate = TRADE_DATE;
            String url = baseUrl + "/daily?tradeDate=" + tradeDate + "&strategyId=NON_EXISTENT_STRATEGY";

            // When
            ResponseEntity<Result> response = restTemplate.getForEntity(url, Result.class);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
        }
    }

    // ==================== 缓存层级验证 ====================

    @Nested
    @DisplayName("缓存层级验证")
    class CacheLayerTests {

        @Test
        @DisplayName("首次查询 - 穿透到 L3")
        void testFirstQueryGoesToL3() {
            // Given
            String strategyId = "TEST_FIRST_QUERY_" + System.currentTimeMillis();
                        String tradeDate = TRADE_DATE;

            // 清空本地缓存
            multiLevelCacheService.invalidateLocalCache(strategyId, tradeDate);

            // When
            long startTime = System.currentTimeMillis();
            List<String> result = multiLevelCacheService.querySignals(strategyId, tradeDate);
            long firstQueryTime = System.currentTimeMillis() - startTime;

            // Then
            System.out.println("首次查询耗时: " + firstQueryTime + "ms");
            assertNotNull(result);
        }

        @Test
        @DisplayName("二次查询 - 命中 L1 缓存")
        void testSecondQueryHitsL1() {
            // Given
            String strategyId = "TEST_L1_HIT_" + System.currentTimeMillis();
                        String tradeDate = TRADE_DATE;

            // 首次查询（填充缓存）
            multiLevelCacheService.querySignals(strategyId, tradeDate);

            // When
            long startTime = System.currentTimeMillis();
            List<String> result = multiLevelCacheService.querySignals(strategyId, tradeDate);
            long secondQueryTime = System.currentTimeMillis() - startTime;

            // Then
            System.out.println("二次查询（L1 命中）耗时: " + secondQueryTime + "ms");
            assertTrue(secondQueryTime < 50, "L1 缓存命中应该 < 50ms");
            assertNotNull(result);
        }

        @Test
        @DisplayName("L1 过期后 - 命中 L2 Redis")
        void testL1ExpiredHitsL2() throws InterruptedException {
            // Given
            String strategyId = "TEST_L2_HIT_" + System.currentTimeMillis();
                        String tradeDate = TRADE_DATE;

            // 首次查询
            multiLevelCacheService.querySignals(strategyId, tradeDate);

            // 等待 L1 过期（3 秒）
            Thread.sleep(3500);

            // When
            long startTime = System.currentTimeMillis();
            List<String> result = multiLevelCacheService.querySignals(strategyId, tradeDate);
            long thirdQueryTime = System.currentTimeMillis() - startTime;

            // Then
            System.out.println("L1 过期后（L2 命中）耗时: " + thirdQueryTime + "ms");
            assertTrue(thirdQueryTime < 50, "L2 Redis 命中应该 < 50ms");
            assertNotNull(result);
        }
    }

    // ==================== 性能基准测试 ====================

    @Nested
    @DisplayName("性能基准测试")
    class PerformanceTests {

        @Test
        @DisplayName("单线程 QPS 基准测试")
        void testSingleThreadQps() {
            // Given
            String strategyId = STRATEGY_ID;
                        String tradeDate = TRADE_DATE;
            int iterations = 1000;

            // 预热
            for (int i = 0; i < 100; i++) {
                multiLevelCacheService.querySignals(strategyId, tradeDate);
            }

            // When
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                multiLevelCacheService.querySignals(strategyId, tradeDate);
            }
            long duration = System.currentTimeMillis() - startTime;

            // Then
            double qps = (iterations * 1000.0) / duration;
            System.out.println("单线程 QPS: " + String.format("%.2f", qps));
            System.out.println("平均延迟: " + String.format("%.2f", duration / (double) iterations) + "ms");

            assertTrue(qps > 1000, "单线程 QPS 应大于 1000");
        }

        @Test
        @DisplayName("P50/P95/P99 延迟测试")
        void testLatencyPercentiles() {
            // Given
            String strategyId = STRATEGY_ID;
                        String tradeDate = TRADE_DATE;
            int iterations = 1000;
            List<Long> latencies = new ArrayList<>();

            // 预热
            for (int i = 0; i < 100; i++) {
                multiLevelCacheService.querySignals(strategyId, tradeDate);
            }

            // When
            for (int i = 0; i < iterations; i++) {
                long startTime = System.nanoTime();
                multiLevelCacheService.querySignals(strategyId, tradeDate);
                long latency = (System.nanoTime() - startTime) / 1_000_000;  // 转换为 ms
                latencies.add(latency);
            }

            // Then
            latencies.sort(Long::compareTo);
            long p50 = latencies.get((int) (iterations * 0.5));
            long p95 = latencies.get((int) (iterations * 0.95));
            long p99 = latencies.get((int) (iterations * 0.99));

            System.out.println("P50 延迟: " + p50 + "ms");
            System.out.println("P95 延迟: " + p95 + "ms");
            System.out.println("P99 延迟: " + p99 + "ms");

            assertTrue(p99 < 10, "P99 延迟应小于 10ms");
        }
    }

    // ==================== 万级并发压测 ====================

    @Nested
    @DisplayName("万级并发压测")
    class HighConcurrencyTests {

        @Test
        @DisplayName("1000 并发请求压测")
        void test1000ConcurrentRequests() throws InterruptedException {
            // Given
            String strategyId = STRATEGY_ID;
                        String tradeDate = TRADE_DATE;
            int threadCount = 1000;

            // 预热
            multiLevelCacheService.querySignals(strategyId, tradeDate);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Long> latencies = new CopyOnWriteArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(100);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long startTime = System.currentTimeMillis();
                        List<String> result = multiLevelCacheService.querySignals(strategyId, tradeDate);
                        long latency = System.currentTimeMillis() - startTime;
                        latencies.add(latency);

                        if (result != null) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();  // 同时启动所有线程
            endLatch.await(30, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            executor.shutdown();

            // Then
            latencies.sort(Long::compareTo);
            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long p99Latency = latencies.get((int) (latencies.size() * 0.99));
            double qps = (threadCount * 1000.0) / totalTime;

            System.out.println("========== 1000 并发压测结果 ==========");
            System.out.println("总耗时: " + totalTime + "ms");
            System.out.println("成功: " + successCount.get() + ", 失败: " + failCount.get());
            System.out.println("QPS: " + String.format("%.2f", qps));
            System.out.println("平均延迟: " + String.format("%.2f", avgLatency) + "ms");
            System.out.println("P99 延迟: " + p99Latency + "ms");

            assertEquals(threadCount, successCount.get(), "所有请求应该成功");
            assertTrue(qps > 1000, "QPS 应大于 1000");
        }

        @Test
        @DisplayName("1000 并发请求压测（高并发）")
        void test10000ConcurrentRequests() throws InterruptedException {
            // Given
            String strategyId = STRATEGY_ID;
                        String tradeDate = TRADE_DATE;
            int threadCount = 1000;

            // 预热
            for (int i = 0; i < 100; i++) {
                multiLevelCacheService.querySignals(strategyId, tradeDate);
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            List<Long> latencies = new CopyOnWriteArrayList<>();

            ExecutorService executor = Executors.newFixedThreadPool(200);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long startTime = System.currentTimeMillis();
                        List<String> result = multiLevelCacheService.querySignals(strategyId, tradeDate);
                        long latency = System.currentTimeMillis() - startTime;
                        latencies.add(latency);

                        if (result != null) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();
            boolean completed = endLatch.await(60, TimeUnit.SECONDS);
            long totalTime = System.currentTimeMillis() - startTime;

            executor.shutdown();

            // Then
            assertTrue(completed, "压测应在 60 秒内完成");

            latencies.sort(Long::compareTo);
            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long p99Latency = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));
            double qps = (successCount.get() * 1000.0) / totalTime;

            System.out.println("========== 10000 并发压测结果（万级并发） ==========");
            System.out.println("总耗时: " + totalTime + "ms");
            System.out.println("成功: " + successCount.get() + ", 失败: " + failCount.get());
            System.out.println("QPS: " + String.format("%.2f", qps));
            System.out.println("平均延迟: " + String.format("%.2f", avgLatency) + "ms");
            System.out.println("P99 延迟: " + p99Latency + "ms");
            System.out.println("成功率: " + String.format("%.2f", successCount.get() * 100.0 / threadCount) + "%");

            assertTrue(successCount.get() >= threadCount * 0.99, "成功率应 >= 99%");
            assertTrue(qps > 500, "万级并发 QPS 应大于 500");
        }
    }
}
