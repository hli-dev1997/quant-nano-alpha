package com.hao.datacollector.web.controller;

import com.hao.datacollector.dto.replay.ReplayParamsDTO;
import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.replay.ReplayScheduler;
import com.hao.datacollector.replay.TimeSliceBuffer;
import constants.DateTimeFormatConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * 行情回放控制器
 * <p>
 * 提供回放服务的启动、暂停、恢复、停止等控制接口。
 * 支持通过全局配置或自定义参数启动回放任务。
 * <p>
 * <b>核心执行链路说明：</b>
 * <ol>
 *     <li><b>启动回放 (start/startWithParams)</b>
 *         <ul>
 *             <li>参数校验：验证日期格式 (yyyyMMdd) 等输入合法性。</li>
 *             <li>参数封装：构建 {@link ReplayParamsDTO}，隔离单次运行参数与全局配置。</li>
 *             <li>任务调度：调用 {@link ReplayScheduler#startReplay(ReplayParamsDTO)} 异步启动回放。</li>
 *             <li>数据流转：
 *                 <ul>
 *                     <li><b>预加载：</b> {@link com.hao.datacollector.replay.DataLoader} 从 MySQL 批量加载历史行情。</li>
 *                     <li><b>缓存：</b> 数据存入 {@link TimeSliceBuffer} 内存队列。</li>
 *                     <li><b>推送：</b> 主循环按秒推进虚拟时间，将数据推送到 Kafka (Topic: quotation)。</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *     </li>
 *     <li><b>运行时控制 (Runtime Control)</b>
 *         <ul>
 *             <li><b>暂停/恢复：</b> 修改 {@link ReplayScheduler} 中的 {@code paused} 标志位。</li>
 *             <li><b>调速：</b> 调用 {@link ReplayScheduler#updateSpeed(int)} 动态调整休眠时间，不影响全局配置。</li>
 *             <li><b>停止：</b> 设置 {@code running=false} 并清空缓冲区。</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * @author hli
 * @date 2026-01-01
 */
@Slf4j
@RestController
@RequestMapping("/replay")
@Tag(name = "行情回放", description = "行情回放服务控制接口")
public class ReplayController {

    /** 回放调度器核心组件，负责任务控制和时间推进 */
    private final ReplayScheduler replayScheduler;

    /** 全局回放配置属性 (只读引用，不修改) */
    private final ReplayProperties replayConfig;

    /** 日期格式化器 (yyyyMMdd) */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);

    /**
     * 显式构造函数，确保依赖注入成功
     *
     * @param replayScheduler 回放调度器核心组件
     * @param replayConfig    全局回放配置
     */
    public ReplayController(ReplayScheduler replayScheduler,
                            ReplayProperties replayConfig) {
        Assert.notNull(replayScheduler, "ReplayScheduler must not be null");
        Assert.notNull(replayConfig, "ReplayProperties must not be null");

        this.replayScheduler = replayScheduler;
        this.replayConfig = replayConfig;

        log.info("ReplayController initialized with config: {}", replayConfig);
    }

    /**
     * 启动回放（使用默认配置）
     * <p>
     * 使用 application.yml 中的全局配置启动回放任务。
     * 如果全局配置 enabled=false，则拒绝启动。
     *
     * @return 操作结果 Map，包含 success 状态和 message 提示
     */
    @PostMapping("/start")
    @Operation(summary = "启动回放", description = "启动行情回放服务，使用配置文件中的参数")
    public Map<String, Object> start() {
        log.info("收到启动回放请求|Replay_start_request");

        if (!replayConfig.isEnabled()) {
            return buildResponse(false, "回放模式未启用，请在配置文件中设置 replay.enabled=true");
        }

        replayScheduler.startReplay();
        return buildResponse(true, "回放已启动");
    }

    /**
     * 使用自定义参数启动回放
     * <p>
     * 允许用户在请求中指定回放时间段、速度和股票范围。
     * <b>注意：</b> 此接口不修改全局配置，仅对本次回放任务生效。
     *
     * @param startDate       回放起始日期 (yyyyMMdd)，可选，默认使用配置值
     * @param endDate         回放结束日期 (yyyyMMdd)，可选，默认使用配置值
     * @param speedMultiplier 回放速度倍数 (0=最快)，可选，默认使用配置值
     * @param stockCodes      股票代码列表 (逗号分隔)，可选，默认使用配置值
     * @return 操作结果 Map
     */
    @PostMapping("/start-with-params")
    @Operation(summary = "自定义参数启动回放", description = "使用请求参数覆盖配置启动回放")
    public Map<String, Object> startWithParams(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer speedMultiplier,
            @RequestParam(required = false) String stockCodes
    ) {
        log.info("收到自定义参数启动回放请求|Replay_start_custom,startDate={},endDate={},speed={}",
                startDate, endDate, speedMultiplier);

        // 1. 参数校验
        try {
            if (startDate != null) LocalDate.parse(startDate, DATE_FORMATTER);
            if (endDate != null) LocalDate.parse(endDate, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return buildResponse(false, "日期格式错误，请使用 yyyyMMdd 格式，例如 20260101");
        }

        // 2. 构建 DTO (合并默认配置与自定义参数)
        ReplayParamsDTO params = ReplayParamsDTO.builder()
                .startDate(startDate != null ? startDate : replayConfig.getStartDate())
                .endDate(endDate != null ? endDate : replayConfig.getEndDate())
                .speedMultiplier(speedMultiplier != null ? speedMultiplier : replayConfig.getSpeedMultiplier())
                .stockCodes(stockCodes != null ? stockCodes : replayConfig.getStockCodes())
                // 以下参数使用默认配置
                .preloadMinutes(replayConfig.getPreloadMinutes())
                .bufferMaxSize(replayConfig.getBufferMaxSize())
                .build();

        // 3. 启动回放 (不修改全局 config)
        replayScheduler.startReplay(params);

        return buildResponse(true, "回放已启动（自定义参数）");
    }

    /**
     * 暂停回放
     * <p>
     * 挂起当前回放任务，停止时间推进和数据推送。
     *
     * @return 操作结果 Map
     */
    @PostMapping("/pause")
    @Operation(summary = "暂停回放")
    public Map<String, Object> pause() {
        replayScheduler.pause();
        return buildResponse(true, "回放已暂停");
    }

    /**
     * 恢复回放
     * <p>
     * 继续之前暂停的回放任务。
     *
     * @return 操作结果 Map
     */
    @PostMapping("/resume")
    @Operation(summary = "恢复回放")
    public Map<String, Object> resume() {
        replayScheduler.resume();
        return buildResponse(true, "回放已恢复");
    }

    /**
     * 停止回放
     * <p>
     * 终止当前回放任务，并清空内存缓冲区。
     *
     * @return 操作结果 Map
     */
    @PostMapping("/stop")
    @Operation(summary = "停止回放")
    public Map<String, Object> stop() {
        replayScheduler.stop();
        return buildResponse(true, "回放已停止");
    }

    /**
     * 获取回放状态
     * <p>
     * 返回当前回放任务的运行状态、进度、缓冲区情况以及生效的配置信息。
     * 如果任务正在运行，返回的是当前实际使用的配置（可能与全局配置不同）。
     *
     * @return 包含状态详情的 Map
     */
    @GetMapping("/status")
    @Operation(summary = "获取回放状态")
    public Map<String, Object> getStatus() {
        ReplayScheduler.ReplayStatus status = replayScheduler.getStatus();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("running", status.running());
        result.put("paused", status.paused());
        result.put("virtualTime", status.getVirtualTimeStr());
        result.put("virtualTimestamp", status.virtualTimestamp());
        result.put("bufferSize", status.bufferSize());
        result.put("totalSentCount", status.totalSentCount());

        // 如果正在运行，显示当前实际生效的配置；否则显示全局配置
        if (status.running() && status.activeConfig() != null) {
            result.put("config", status.activeConfig());
        } else {
            result.put("config", buildConfigInfo());
        }

        return result;
    }

    /**
     * 获取当前全局配置
     * <p>
     * 返回 application.yml 中配置的默认回放参数。
     *
     * @return 配置信息 Map
     */
    @GetMapping("/config")
    @Operation(summary = "获取回放配置")
    public Map<String, Object> getConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("config", buildConfigInfo());
        return result;
    }

    /**
     * 动态调整回放速度
     * <p>
     * 在回放运行过程中实时调整播放速度，无需重启任务。
     * 此操作仅影响当前运行的任务，不修改全局配置。
     *
     * @param multiplier 速度倍数 (例如：1=实时，10=10倍速，0=极速)
     * @return 操作结果 Map
     */
    @PostMapping("/speed")
    @Operation(summary = "调整回放速度", description = "动态调整回放速度倍数，0=最快速度")
    public Map<String, Object> setSpeed(@RequestParam int multiplier) {
        // 调用 Scheduler 的动态调速方法，不再修改全局 Config
        replayScheduler.updateSpeed(multiplier);

        log.info("回放速度已调整|Replay_speed_changed,multiplier={}", multiplier);
        return buildResponse(true, "速度已调整为 " + multiplier + " 倍速");
    }

    private Map<String, Object> buildResponse(boolean success, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> buildConfigInfo() {
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("enabled", replayConfig.isEnabled());
        configInfo.put("startDate", replayConfig.getStartDate());
        configInfo.put("endDate", replayConfig.getEndDate());
        configInfo.put("speedMultiplier", replayConfig.getSpeedMultiplier());
        configInfo.put("preloadMinutes", replayConfig.getPreloadMinutes());
        configInfo.put("bufferMaxSize", replayConfig.getBufferMaxSize());
        configInfo.put("stockCodes", replayConfig.getStockCodes());
        return configInfo;
    }
}
