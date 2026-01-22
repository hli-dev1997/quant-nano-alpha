package com.hao.datacollector.service.impl;

import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.dto.quotation.HistoryTrendDTO;
import com.hao.datacollector.service.QuotationService;
import com.hao.datacollector.service.StrategyPreparationService;
import enums.strategy.StrategyRedisKeyEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import util.DateUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StrategyPreparationServiceImpl 集成测试
 *
 * <p><b>测试目的：</b></p>
 * <ol>
 *     <li>验证九转策略预热接口的核心逻辑正确性。</li>
 *     <li>验证交易日校验、历史数据查询、收盘价提取、Redis存储全链路。</li>
 *     <li>确保测试后清理Redis数据，不产生脏数据。</li>
 * </ol>
 *
 * <p><b>测试策略：</b></p>
 * - 使用 @AfterEach 清理每个测试产生的Redis Key
 * - 测试覆盖：正常流程、边界条件、异常场景
 *
 * @author hli
 * @date 2026-01-02
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StrategyPreparationServiceImplTest {

    @Autowired
    private StrategyPreparationService strategyPreparationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 测试使用的交易日
     * 选择一个确定存在于交易日历中的日期
     */
    private LocalDate testTradeDate;

    /**
     * 测试产生的Redis Key，用于清理
     */
    private String testRedisKey;

    @BeforeEach
    void setUp() {
        // 实现思路：
        // 1. 从交易日历中选择一个有效的交易日
        // 2. 确保该日期前有足够的历史数据（至少20天）
        List<LocalDate> tradeDates = DateCache.Year2025TradeDateList;
        if (tradeDates != null && tradeDates.size() > 25) {
            // 选择第25个交易日，确保前面有20天可查
//            testTradeDate = tradeDates.get(24);
            testTradeDate = DateCache.Year2025TradeDateList.getLast();
        } else {
            // 回退到使用2026年的某个交易日
            testTradeDate = LocalDate.of(2026, 2, 10);
        }
        
        // 构建测试Key
        String dateSuffix = testTradeDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        testRedisKey = StrategyRedisKeyEnum.NINE_TURN_PREHEAT.buildKey(dateSuffix);
        
        log.info("测试初始化|Test_setup,testTradeDate={},redisKey={}", testTradeDate, testRedisKey);
    }

    @AfterEach
    void tearDown() {
        // 实现思路：
        // 1. 清理测试产生的Redis数据
        // 2. 避免对其他测试或生产环境造成影响
        if (testRedisKey != null) {
            Boolean deleted = stringRedisTemplate.delete(testRedisKey);
            log.info("测试清理|Test_cleanup,redisKey={},deleted={}", testRedisKey, deleted);
        }
    }

    // ==================== 正常流程测试 ====================

    /**
     * 测试场景：正常预热流程
     * 
     * 测试思路：
     * 1. 使用有效交易日调用预热方法
     * 2. 验证返回的股票数量大于0
     * 3. 验证Redis中写入了数据
     * 4. 验证数据结构正确（Hash结构，Value为JSON数组）
     */
    @Test
    @Order(1)
    @DisplayName("集成测试_正常预热流程_验证Redis数据写入")
    void testPrepareNineTurnData_Success() {
        // 前置检查：确保交易日历已初始化
        assertNotNull(DateCache.Year2025TradeDateList, "交易日历应已初始化");
        assertTrue(DateCache.Year2025TradeDateList.size() > 20, "交易日历应有足够数据");

        log.info("开始测试：正常预热流程，交易日={}", testTradeDate);

        // 执行预热
        int stockCount = strategyPreparationService.prepareNineTurnData(testTradeDate);

        // 验证返回值
        assertTrue(stockCount > 0, "预热应成功处理至少一只股票");
        log.info("预热完成，处理股票数={}", stockCount);

        // 验证Redis数据
        Boolean exists = stringRedisTemplate.hasKey(testRedisKey);
        assertTrue(exists, "Redis Key应存在");

        // 验证Hash结构
        Set<Object> fields = stringRedisTemplate.opsForHash().keys(testRedisKey);
        assertNotNull(fields, "Hash字段集不应为null");
        assertFalse(fields.isEmpty(), "Hash应包含股票数据");
        assertEquals(stockCount, fields.size(), "Hash字段数应与返回的股票数一致");

        // 随机验证一条数据的格式
        Object firstField = fields.iterator().next();
        Object value = stringRedisTemplate.opsForHash().get(testRedisKey, firstField);
        assertNotNull(value, "Hash Value不应为null");
        assertTrue(value.toString().startsWith("["), "Value应为JSON数组格式");
        assertTrue(value.toString().endsWith("]"), "Value应为JSON数组格式");

        log.info("测试通过：Redis数据结构验证成功，示例字段={}", firstField);
    }

    /**
     * 测试场景：验证收盘价数据正确性
     * 
     * 测试思路：
     * 1. 预热后，随机取一只股票的数据
     * 2. 验证JSON数组包含 ClosePriceDTO 格式的对象
     * 3. 验证对象中包含 tradeDate 和 closePrice 字段
     */
    @Test
    @Order(2)
    @DisplayName("集成测试_验证收盘价数据格式")
    void testPrepareNineTurnData_DataFormat() {
        log.info("开始测试：验证收盘价数据格式（ClosePriceDTO）");

        // 执行预热
        int stockCount = strategyPreparationService.prepareNineTurnData(testTradeDate);
        assertTrue(stockCount > 0, "预热应成功");

        // 获取所有股票代码
        Map<Object, Object> allData = stringRedisTemplate.opsForHash().entries(testRedisKey);
        assertFalse(allData.isEmpty(), "应有预热数据");

        // 验证数据格式（ClosePriceDTO JSON 格式）
        int validCount = 0;
        for (Map.Entry<Object, Object> entry : allData.entrySet()) {
            String windCode = entry.getKey().toString();
            String jsonValue = entry.getValue().toString();

            // 验证JSON数组格式
            assertTrue(jsonValue.startsWith("[") && jsonValue.endsWith("]"),
                    "股票 " + windCode + " 的数据应为JSON数组格式");

            // 验证包含 ClosePriceDTO 格式的对象（应包含 tradeDate 和 closePrice 字段）
            assertTrue(jsonValue.contains("tradeDate"),
                    "股票 " + windCode + " 的数据应包含 tradeDate 字段");
            assertTrue(jsonValue.contains("closePrice"),
                    "股票 " + windCode + " 的数据应包含 closePrice 字段");

            // 验证日期格式（yyyyMMdd）
            assertTrue(jsonValue.matches(".*\"tradeDate\":\"\\d{8}\".*"),
                    "tradeDate 应为 yyyyMMdd 格式");

            // 验证至少有一些有效的价格数据
            assertTrue(jsonValue.contains("closePrice\":") && 
                       (jsonValue.matches(".*closePrice\":\\d+\\.\\d+.*") || 
                        jsonValue.contains("closePrice\":null")),
                    "closePrice 应为数字或 null");

            validCount++;
            if (validCount >= 5) break; // 只验证前5只股票
        }

        log.info("测试通过：验证了 {} 只股票的 ClosePriceDTO 数据格式", validCount);
    }

    // ==================== 异常场景测试 ====================

    /**
     * 测试场景：非交易日应抛出异常
     * 
     * 测试思路：
     * 1. 使用一个确定不在交易日历中的日期（如周末或节假日）
     * 2. 验证抛出 IllegalArgumentException
     */
    @Test
    @Order(3)
    @DisplayName("异常测试_非交易日应抛出异常")
    void testPrepareNineTurnData_InvalidTradeDate() {
        // 构造一个非交易日（假设2026-01-01是元旦假期）
        LocalDate nonTradeDate = LocalDate.of(2026, 1, 1);
        
        // 如果元旦恰好在交易日历中，则使用周末
        if (DateCache.CurrentYearTradeDateList != null 
                && DateCache.CurrentYearTradeDateList.contains(nonTradeDate)) {
            // 找一个周六
            nonTradeDate = LocalDate.of(2026, 1, 4);
            while (DateCache.CurrentYearTradeDateList.contains(nonTradeDate)) {
                nonTradeDate = nonTradeDate.plusDays(1);
            }
        }

        final LocalDate testDate = nonTradeDate;
        log.info("开始测试：非交易日异常，测试日期={}", testDate);

        // 验证抛出异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> strategyPreparationService.prepareNineTurnData(testDate),
                "非交易日应抛出 IllegalArgumentException"
        );

        assertTrue(exception.getMessage().contains("非交易日") || 
                   exception.getMessage().contains("Invalid_trade_date"),
                "异常消息应包含非交易日相关信息");

        log.info("测试通过：非交易日正确抛出异常，消息={}", exception.getMessage());
    }

    /**
     * 测试场景：年初交易日不足应抛出异常
     * 
     * 测试思路：
     * 1. 使用年初第一个交易日（前面的历史数据不足20天）
     * 2. 验证抛出 IllegalArgumentException
     */
    @Test
    @Order(4)
    @DisplayName("异常测试_年初交易日不足应抛出异常")
    void testPrepareNineTurnData_InsufficientTradeDays() {
        // 获取年初第一个交易日
        List<LocalDate> tradeDates = DateCache.CurrentYearTradeDateList;
        if (tradeDates == null || tradeDates.isEmpty()) {
            log.warn("交易日历为空，跳过此测试");
            return;
        }

        LocalDate firstTradeDate = tradeDates.get(0);
        log.info("开始测试：年初交易日不足，测试日期={}", firstTradeDate);

        // 验证抛出异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> strategyPreparationService.prepareNineTurnData(firstTradeDate),
                "年初交易日不足应抛出 IllegalArgumentException"
        );

        assertTrue(exception.getMessage().contains("交易日不足") || 
                   exception.getMessage().contains("Insufficient_trade_days"),
                "异常消息应包含交易日不足相关信息");

        log.info("测试通过：年初交易日不足正确抛出异常，消息={}", exception.getMessage());
    }

    // ==================== 工具方法测试 ====================

    /**
     * 测试场景：DateUtil.parseTradeDate 方法测试
     * 
     * 测试思路：
     * 1. 测试空值返回今天
     * 2. 测试 yyyyMMdd 格式
     * 3. 测试 yyyy-MM-dd 格式
     * 4. 测试非法格式抛出异常
     */
    @Test
    @Order(5)
    @DisplayName("单元测试_DateUtil.parseTradeDate")
    void testDateUtil_parseTradeDate() {
        // 空值返回今天
        LocalDate today = DateUtil.parseTradeDate(null);
        assertEquals(LocalDate.now(), today, "空值应返回今天");

        LocalDate todayEmpty = DateUtil.parseTradeDate("  ");
        assertEquals(LocalDate.now(), todayEmpty, "空白字符串应返回今天");

        // yyyyMMdd 格式
        LocalDate date1 = DateUtil.parseTradeDate("20260102");
        assertEquals(LocalDate.of(2026, 1, 2), date1, "应正确解析 yyyyMMdd 格式");

        // yyyy-MM-dd 格式
        LocalDate date2 = DateUtil.parseTradeDate("2026-01-02");
        assertEquals(LocalDate.of(2026, 1, 2), date2, "应正确解析 yyyy-MM-dd 格式");

        // 非法格式
        assertThrows(IllegalArgumentException.class, 
                () -> DateUtil.parseTradeDate("2026/01/02"),
                "非法格式应抛出异常");

        log.info("测试通过：DateUtil.parseTradeDate 方法验证成功");
    }

    /**
     * 测试场景：StrategyRedisKeyEnum 枚举测试
     * 
     * 测试思路：
     * 1. 验证 buildKey 方法
     * 2. 验证 getKeyPrefix 方法
     * 3. 验证各属性值
     */
    @Test
    @Order(6)
    @DisplayName("单元测试_StrategyRedisKeyEnum")
    void testStrategyRedisKeyEnum() {
        StrategyRedisKeyEnum config = StrategyRedisKeyEnum.NINE_TURN_PREHEAT;

        // 验证属性
        assertEquals(24, config.getTtlHours(), "TTL应为24小时");
        assertEquals(20, config.getHistoryDays(), "历史天数应为20");

        // 验证 buildKey
        String key = config.buildKey("20260102");
        assertTrue(key.contains("20260102"), "Key构建应包含日期后缀");

        // 验证 getKeyPrefix
        String prefix = config.getKeyPrefix();
        assertNotNull(prefix, "Key前缀不应为null");
        assertTrue(prefix.endsWith(":"), "Key前缀应以冒号结尾");

        log.info("测试通过：StrategyRedisKeyEnum 枚举验证成功");
    }
}
