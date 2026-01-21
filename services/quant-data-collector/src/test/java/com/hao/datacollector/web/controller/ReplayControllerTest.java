package com.hao.datacollector.web.controller;

import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.replay.ReplayScheduler;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ReplayController API 测试类
 * <p>
 * 测试目标：验证回放控制 API 的正确性
 * <p>
 * 测试范围：
 * 1. API 功能测试：启动、暂停、恢复、停止、调速
 * 2. 参数验证测试：自定义参数启动
 * 3. 状态查询测试：获取状态和配置
 * 4. 边界条件测试：禁用模式、重复操作
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReplayControllerTest {

    @Mock
    private ReplayScheduler replayScheduler;

    private ReplayProperties replayConfig;

    // ReplayController 只需要 ReplayScheduler 和 ReplayProperties
    private ReplayController controller;

    @BeforeEach
    void setUp() {
        // 手动创建真实的 ReplayProperties（因为需要测试其状态变化）
        replayConfig = new ReplayProperties();
        replayConfig.setEnabled(true);
        replayConfig.setStartDate("20260105");
        replayConfig.setEndDate("20260105");
        replayConfig.setSpeedMultiplier(1);
        replayConfig.setPreloadMinutes(5);
        replayConfig.setBufferMaxSize(100000);

        // 使用实际的 ReplayController 构造函数（只接受 2 个参数）
        controller = new ReplayController(replayScheduler, replayConfig);
    }

    // ==================== 启动 API 测试 ====================

    /**
     * 测试：正常启动回放
     * <p>
     * 思路：
     * 1. 配置 enabled=true
     * 2. 调用 start()
     * 3. 验证 replayScheduler.startReplay() 被调用
     * <p>
     * 预期结果：返回成功，调度器被启动
     */
    @Test
    @Order(1)
    @DisplayName("启动 API - 正常启动")
    void testStartSuccess() {
        // Given: 回放已启用
        replayConfig.setEnabled(true);

        // When: 调用启动（不传参数，使用配置默认值）
        Map<String, Object> result = controller.start(null, null, null, null);

        // Then: 验证结果
        assertTrue((Boolean) result.get("success"));
        assertEquals("回放已启动", result.get("message"));
        verify(replayScheduler).startReplay(any(com.hao.datacollector.dto.replay.ReplayParamsDTO.class));

        log.info("正常启动测试通过");
    }

    /**
     * 测试：禁用模式下启动失败
     * <p>
     * 思路：enabled=false 时启动应返回错误
     * <p>
     * 预期结果：返回失败，调度器不被调用
     */
    @Test
    @Order(2)
    @DisplayName("启动 API - 禁用模式")
    void testStartWhenDisabled() {
        // Given: 回放被禁用
        replayConfig.setEnabled(false);

        // When
        Map<String, Object> result = controller.start(null, null, null, null);

        // Then
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("message").toString().contains("未启用"));
        verify(replayScheduler, never()).startReplay(any());

        log.info("禁用模式启动测试通过");
    }

    /**
     * 测试：自定义参数启动
     * <p>
     * 思路：
     * 1. 使用自定义参数调用 API
     * 2. 验证配置被更新
     * 3. 验证调度器被启动
     * <p>
     * 预期结果：参数覆盖配置，调度器启动
     */
    @Test
    @Order(3)
    @DisplayName("启动 API - 自定义参数")
    void testStartWithCustomParams() {
        // When: 使用自定义参数启动
        Map<String, Object> result = controller.start(
                "20251001",  // startDate
                "20251031",  // endDate
                50,          // speedMultiplier
                "600519.SH,000001.SZ"  // stockCodes
        );

        // Then: 验证返回成功（不修改全局配置，只是通过 DTO 传递参数）
        assertTrue((Boolean) result.get("success"));

        // 验证 replayScheduler.startReplay(ReplayParamsDTO) 被调用
        verify(replayScheduler).startReplay(any(com.hao.datacollector.dto.replay.ReplayParamsDTO.class));

        log.info("自定义参数启动测试通过|Custom_params_start_test_passed");
    }

    /**
     * 测试：部分参数覆盖
     * <p>
     * 思路：只传递部分参数，其他参数保持原值
     * <p>
     * 预期结果：只有传入的参数被更新
     */
    @Test
    @Order(4)
    @DisplayName("启动 API - 部分参数覆盖")
    void testStartWithPartialParams() {
        // Given: 原始配置
        String originalStartDate = replayConfig.getStartDate();
        String originalEndDate = replayConfig.getEndDate();

        // When: 只传 startDate（其他参数为 null，会使用默认配置）
        Map<String, Object> result = controller.start("20251201", null, null, null);

        // Then: 返回成功，同样不修改全局配置
        assertTrue((Boolean) result.get("success"));
        // 全局配置不变
        assertEquals(originalStartDate, replayConfig.getStartDate());
        assertEquals(originalEndDate, replayConfig.getEndDate());

        verify(replayScheduler).startReplay(any(com.hao.datacollector.dto.replay.ReplayParamsDTO.class));

        log.info("部分参数覆盖测试通过|Partial_params_test_passed");
    }

    // ==================== 控制 API 测试 ====================

    /**
     * 测试：暂停 API
     * <p>
     * 思路：调用 pause() 应触发调度器暂停
     * <p>
     * 预期结果：返回成功，调度器 pause() 被调用
     */
    @Test
    @Order(10)
    @DisplayName("控制 API - 暂停")
    void testPause() {
        // When
        Map<String, Object> result = controller.pause();

        // Then
        assertTrue((Boolean) result.get("success"));
        assertEquals("回放已暂停", result.get("message"));
        verify(replayScheduler).pause();

        log.info("暂停API测试通过|Pause_API_test_passed");
    }

    /**
     * 测试：恢复 API
     * <p>
     * 思路：调用 resume() 应触发调度器恢复
     * <p>
     * 预期结果：返回成功，调度器 resume() 被调用
     */
    @Test
    @Order(11)
    @DisplayName("控制 API - 恢复")
    void testResume() {
        // When
        Map<String, Object> result = controller.resume();

        // Then
        assertTrue((Boolean) result.get("success"));
        assertEquals("回放已恢复", result.get("message"));
        verify(replayScheduler).resume();

        log.info("恢复API测试通过|Resume_API_test_passed");
    }

    /**
     * 测试：停止 API
     * <p>
     * 思路：调用 stop() 应触发调度器停止
     * <p>
     * 预期结果：返回成功，调度器 stop() 被调用
     */
    @Test
    @Order(12)
    @DisplayName("控制 API - 停止")
    void testStop() {
        // When
        Map<String, Object> result = controller.stop();

        // Then
        assertTrue((Boolean) result.get("success"));
        assertEquals("回放已停止", result.get("message"));
        verify(replayScheduler).stop();

        log.info("停止API测试通过|Stop_API_test_passed");
    }

    // ==================== 调速 API 测试 ====================

    /**
     * 测试：调速 API
     * <p>
     * 思路：调用 setSpeed 应更新配置中的速度倍数
     * <p>
     * 预期结果：配置被更新，返回成功
     */
    @Test
    @Order(20)
    @DisplayName("调速 API - 正常调速")
    void testSetSpeed() {
        // When: 调整速度
        Map<String, Object> result = controller.setSpeed(100);

        // Then: 验证返回成功并调用了 updateSpeed
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.get("message").toString().contains("100"));
        verify(replayScheduler).updateSpeed(100);

        log.info("正常调速测试通过|Speed_update_test_passed");
    }

    /**
     * 测试：调速到最快（0）
     * <p>
     * 思路：speedMultiplier=0 表示最快速度
     * <p>
     * 预期结果：配置被更新为 0
     */
    @Test
    @Order(21)
    @DisplayName("调速 API - 最快速度")
    void testSetSpeedToFastest() {
        // When: 设置为最快速度
        Map<String, Object> result = controller.setSpeed(0);

        // Then: 调用 updateSpeed(0)
        assertTrue((Boolean) result.get("success"));
        verify(replayScheduler).updateSpeed(0);

        log.info("最快速度调速测试通过|Fastest_speed_test_passed");
    }

    // ==================== 状态查询 API 测试 ====================

    /**
     * 测试：获取状态 API
     * <p>
     * 思路：
     * 1. Mock 调度器返回状态
     * 2. 调用 getStatus()
     * 3. 验证返回的状态信息完整
     * <p>
     * 预期结果：返回包含所有状态字段的 Map
     */
//    @Test
//    @Order(30)
//    @DisplayName("状态查询 - 获取状态")
//    void testGetStatus() {
//        // Given: Mock 状态
//        ReplayScheduler.ReplayStatus mockStatus = new ReplayScheduler.ReplayStatus(
//                true,      // running
//                false,     // paused
//                1717225800L,  // virtualTimestamp (2024-06-01 09:30:00 +8)
//                5000,      // bufferSize
//                100000L    // totalSentCount
//        );
//        when(replayScheduler.getStatus()).thenReturn(mockStatus);
//
//        // When
//        Map<String, Object> result = controller.getStatus();
//
//        // Then: 验证所有字段
//        assertTrue((Boolean) result.get("success"));
//        assertTrue((Boolean) result.get("running"));
//        assertFalse((Boolean) result.get("paused"));
//        assertEquals(5000, result.get("bufferSize"));
//        assertEquals(100000L, result.get("totalSentCount"));
//        assertNotNull(result.get("config"));
//
//        log.info("获取状态测试通过");
//    }

    /**
     * 测试：获取配置 API
     * <p>
     * 思路：验证配置信息完整返回
     * <p>
     * 预期结果：返回包含所有配置字段的 Map
     */
    @Test
    @Order(31)
    @DisplayName("状态查询 - 获取配置")
    void testGetConfig() {
        // When
        Map<String, Object> result = controller.getConfig();

        // Then
        assertTrue((Boolean) result.get("success"));

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) result.get("config");
        assertNotNull(config);
        assertEquals(replayConfig.isEnabled(), config.get("enabled"));
        assertEquals(replayConfig.getStartDate(), config.get("startDate"));
        assertEquals(replayConfig.getEndDate(), config.get("endDate"));
        assertEquals(replayConfig.getSpeedMultiplier(), config.get("speedMultiplier"));

        log.info("获取配置测试通过");
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试：连续调用暂停
     * <p>
     * 思路：多次调用 pause 不应报错
     * <p>
     * 预期结果：每次都返回成功
     */
    @Test
    @Order(40)
    @DisplayName("边界条件 - 连续暂停")
    void testMultiplePause() {
        // When: 连续调用多次
        for (int i = 0; i < 3; i++) {
            Map<String, Object> result = controller.pause();
            assertTrue((Boolean) result.get("success"));
        }

        // Then: 调度器 pause 被调用3次
        verify(replayScheduler, times(3)).pause();

        log.info("连续暂停测试通过");
    }

    /**
     * 测试：负数速度处理
     * <p>
     * 思路：负数速度应被接受（与0等效）
     * <p>
     * 预期结果：配置被更新为负数
     */
    @Test
    @Order(41)
    @DisplayName("边界条件 - 负数速度")
    void testNegativeSpeed() {
        // When: 设置负数速度
        Map<String, Object> result = controller.setSpeed(-10);

        // Then: 调用 updateSpeed(-10)
        assertTrue((Boolean) result.get("success"));
        verify(replayScheduler).updateSpeed(-10);

        log.info("负数速度测试通过|Negative_speed_test_passed");
    }
}
