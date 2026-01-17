package com.hao.datacollector.web.controller;

import com.hao.datacollector.properties.ReplayProperties;
import com.hao.datacollector.replay.ReplayScheduler;
import com.hao.datacollector.replay.TimeSliceBuffer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 行情回放控制器
 * <p>
 * 提供回放服务的启动、暂停、恢复、停止等控制接口。
 * <p>
 * <b>核心执行链路说明：</b>
 * <ol>
 *     <li><b>启动回放 (start/startWithParams)</b>
 *         <ul>
 *             <li>调用 {@link ReplayScheduler#startReplay()} 异步启动回放任务。</li>
 *             <li>{@link ReplayScheduler#doReplay()} 初始化虚拟时钟，并启动预加载线程 {@link ReplayScheduler#startPreloader(java.time.LocalDateTime)}。</li>
 *             <li><b>预加载：</b> {@link com.hao.datacollector.replay.DataLoader#loadTimeSlice} 调用 {@link com.hao.datacollector.service.QuotationService#getHistoryTrendDataByStockList}
 *                 从 MySQL (tb_quotation_history_hot/warm) 批量加载历史行情数据。</li>
 *             <li><b>缓存：</b> 加载的数据存入 {@link TimeSliceBuffer} 内存队列。</li>
 *             <li><b>推送：</b> 主循环按秒推进虚拟时间，调用 {@link TimeSliceBuffer#pollSlice(long)} 取出当前秒的数据。</li>
 *             <li><b>发送：</b> 调用 {@link com.hao.datacollector.service.KafkaProducerService#sendBatchHighPerformance} 将数据推送到 Kafka (Topic: quotation)。</li>
 *         </ul>
 *     </li>
 *     <li><b>暂停/恢复 (pause/resume)</b>
 *         <ul>
 *             <li>修改 {@link ReplayScheduler} 中的 {@code paused} 原子标志位，控制主循环的挂起与继续。</li>
 *         </ul>
 *     </li>
 *     <li><b>调速 (setSpeed)</b>
 *         <ul>
 *             <li>修改 {@link ReplayProperties#setSpeedMultiplier}，影响主循环中的 {@code sleepForReplay()} 休眠时间。</li>
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

    private final ReplayScheduler replayScheduler;
    private final ReplayProperties replayConfig;
    private final TimeSliceBuffer timeSliceBuffer;

    /**
     * 显式构造函数，确保依赖注入成功
     */
    public ReplayController(ReplayScheduler replayScheduler,
                            ReplayProperties replayConfig,
                            TimeSliceBuffer timeSliceBuffer) {
        Assert.notNull(replayScheduler, "ReplayScheduler must not be null");
        Assert.notNull(replayConfig, "ReplayProperties must not be null");
        Assert.notNull(timeSliceBuffer, "TimeSliceBuffer must not be null");

        this.replayScheduler = replayScheduler;
        this.replayConfig = replayConfig;
        this.timeSliceBuffer = timeSliceBuffer;

        log.info("ReplayController initialized with config: {}", replayConfig);
    }

    /**
     * 启动回放
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

        // 临时覆盖配置
        if (startDate != null) replayConfig.setStartDate(startDate);
        if (endDate != null) replayConfig.setEndDate(endDate);
        if (speedMultiplier != null) replayConfig.setSpeedMultiplier(speedMultiplier);
        if (stockCodes != null) replayConfig.setStockCodes(stockCodes);

        // 临时启用
        replayConfig.setEnabled(true);

        replayScheduler.startReplay();
        return buildResponse(true, "回放已启动（自定义参数）");
    }

    /**
     * 暂停回放
     */
    @PostMapping("/pause")
    @Operation(summary = "暂停回放")
    public Map<String, Object> pause() {
        replayScheduler.pause();
        return buildResponse(true, "回放已暂停");
    }

    /**
     * 恢复回放
     */
    @PostMapping("/resume")
    @Operation(summary = "恢复回放")
    public Map<String, Object> resume() {
        replayScheduler.resume();
        return buildResponse(true, "回放已恢复");
    }

    /**
     * 停止回放
     */
    @PostMapping("/stop")
    @Operation(summary = "停止回放")
    public Map<String, Object> stop() {
        replayScheduler.stop();
        return buildResponse(true, "回放已停止");
    }

    /**
     * 获取回放状态
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
        result.put("config", buildConfigInfo());

        return result;
    }

    /**
     * 获取当前配置
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
     * 动态调整速度
     */
    @PostMapping("/speed")
    @Operation(summary = "调整回放速度", description = "动态调整回放速度倍数，0=最快速度")
    public Map<String, Object> setSpeed(@RequestParam int multiplier) {
        replayConfig.setSpeedMultiplier(multiplier);
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
