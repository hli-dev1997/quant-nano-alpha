package com.hao.strategyengine.api.controller;

/**
 * 类说明 / Class Description:
 * 中文：策略执行入口控制器，负责接收请求、构建上下文并以SSE流式返回执行结果，同时将结果异步发布到消息系统。
 * English: Entry controller for strategy execution; receives requests, builds context, streams results via SSE, and asynchronously publishes outcomes to the message bus.
 *
 * 使用场景 / Use Cases:
 * 中文：用于实时策略计算、回测任务、长耗时信号监控的HTTP接口入口，适配前端事件流消费。
 * English: HTTP entry for real-time strategy runs, backtesting tasks, and long-running signal monitoring, suitable for frontend event-stream consumption.
 *
 * 设计目的 / Design Purpose:
 * 中文：隔离业务计算与对外暴露层，采用外观与并发分离的架构，提升吞吐与响应体验。
 * English: Decouple business computation from exposure layer; apply facade and concurrency separation to improve throughput and response experience.
 */

import com.hao.strategyengine.common.model.core.StrategyContext;
import com.hao.strategyengine.common.model.request.StrategyRequest;
import com.hao.strategyengine.common.model.response.StrategyResultBundle;
import com.hao.strategyengine.core.facade.StrategyEngineFacade;
import com.hao.strategyengine.integration.kafka.KafkaResultPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * ===============================================================
 * 【类名】：StrategyController（策略执行入口层）
 * ===============================================================
 * <p>
 * 【功能定位】：
 * ⦿ 提供对外 HTTP 接口，作为策略引擎的统一入口；
 * ⦿ 接收用户请求（策略组合、标的、附加参数）；
 * ⦿ 封装上下文（StrategyContext）并调用 Facade 统一调度；
 * ⦿ 通过 Server-Sent Events (SSE) 实时推送计算结果；
 * ⦿ 异步推送计算结果到 Kafka 消息队列。
 * <p>
 * 【核心思路】：
 * - Controller 不直接参与业务计算，只负责协调调用；
 * - 计算逻辑交由 Facade + 分布式锁 + Dispatcher 完成；
 * - 结果流式返回，避免长连接阻塞；
 * - SSE 适合策略计算类长耗时接口。
 * <p>
 * 【执行链位置】：
 *  属于系统调用链的「第 1 层」：
 * Controller(第1层) → Service/Facade(第2~3层) → Lock(第4层)
 * → Dispatcher(第5层) → Strategy(第6层)
 * <p>
 * 【执行流程】：
 * ┌───────────────────────────────────────────┐
 * │ Step 1：接收外部 POST 请求 (/api/strategy/execute) │
 * │ Step 2：封装请求参数为 StrategyContext              │
 * │ Step 3：调用 Facade 执行策略组合（分布式锁保护）     │
 * │ Step 4：获取计算结果 StrategyResultBundle           │
 * │ Step 5：推送结果到 Kafka 和 SSE 客户端               │
 * └───────────────────────────────────────────┘
 * <p>
 * 【响应机制】：
 * - 返回类型为 SseEmitter：服务端实时推送结果；
 * - 客户端可使用 EventSource 监听返回；
 * - 适用于“策略执行、回测、信号监控”等流式任务。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/strategy")
public class StrategyController {

    /**
     * 外观层：封装锁、分发、缓存、Kafka 的统一执行入口
     */
    private final StrategyEngineFacade engine;

    /**
     * Kafka 结果发布器，用于异步广播计算结果
     */
    private final KafkaResultPublisher kafkaPublisher;

    /**
     * ===============================================================
     * 【方法名】：execute
     * ===============================================================
     * <p>
     * 【功能】：
     * 接收策略执行请求并异步返回结果。
     * 使用 SSE 实现实时响应流，避免前端超时。
     * <p>
     * 【参数】：
     *
     * @param req 策略执行请求体（包含 userId、策略ID集合、symbol、额外参数）
     *            <p>
     *            【返回】：
     *            SseEmitter（服务端事件流，推送结果或异常）
     *            <p>
     *            【执行流程】：
     *            ① 创建 SseEmitter（30 秒超时）；
     *            ② 异步线程构建 StrategyContext；
     *            ③ 调用 Facade 执行所有策略；
     *            ④ 推送结果到 Kafka；
     *            ⑤ 发送 SSE 响应流；
     *            ⑥ 处理异常情况。
     */
    @PostMapping(value = "/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    /**
     * 方法说明 / Method Description:
     * 中文：接收策略执行请求，创建SSE通道，异步执行业务计算并将结果以事件流返回客户端，同时广播到Kafka。
     * English: Accepts strategy execution request, creates SSE channel, executes business asynchronously, streams result to client, and broadcasts to Kafka.
     *
     * 参数 / Parameters:
     * @param req 中文说明：策略执行请求体（包含用户ID、策略ID集合、交易标的、额外参数） / English: Strategy execution payload (user ID, strategy IDs, symbol, extras)
     *
     * 返回值 / Return:
     * 中文：SseEmitter（服务端事件流对象，用于持续推送结果或错误事件） / English: SseEmitter (server-sent event stream object for pushing results or errors)
     *
     * 异常 / Exceptions:
     * 中文：可能抛出IO异常（推送失败）、运行时异常（上下文构建或执行错误），均会转化为SSE错误事件并结束连接 / English: May throw IO exceptions (push failure) or runtime exceptions (context build or execution errors); converted to SSE error events and connection termination.
     */
    public SseEmitter execute(@RequestBody StrategyRequest req) {
        // 中文：创建SSE通道，设置超时保证连接释放
        // English: Create SSE channel and set timeout to ensure connection release
        // Step 1⃣ 创建 SSE 流，允许 30 秒超时
        SseEmitter emitter = new SseEmitter(30_000L);

        // 中文：以异步方式执行，避免阻塞控制器线程，提高并发能力
        // English: Run asynchronously to avoid blocking controller thread and improve concurrency
        // Step 2⃣ 异步执行任务，防止 Controller 阻塞
        CompletableFuture.runAsync(() -> {
            try {
                // 中文：构建领域上下文，封装用户、标的、扩展参数与请求时间
                // English: Build domain context encapsulating user, symbol, extra params, and request timestamp
                // Step 3⃣ 构建策略上下文对象（封装调用环境）
                StrategyContext ctx = StrategyContext.builder()
                        .userId(req.getUserId())
                        .symbol(req.getSymbol())
                        .extra(req.getExtra())
                        .requestTime(Instant.now())
                        .build();

                // 中文：通过外观统一调度策略执行，内部含并发与锁控制以保障幂等
                // English: Delegate strategy execution to facade with concurrency and lock control to ensure idempotency
                // Step 4⃣ 调用 Facade 执行策略组合（内部含锁/并发控制）
                StrategyResultBundle bundle = engine.executeAll(req.getUserId(), req.getStrategyIds(), ctx);

                // 中文：结果异步广播到消息系统，解耦后续消费方
                // English: Broadcast results asynchronously to message system to decouple downstream consumers
                // Step 5⃣ 异步发布 Kafka 消息（非阻塞）
                kafkaPublisher.publish("quant-strategy-result", bundle);

                // 中文：通过SSE向前端推送聚合结果，实现流式响应
                // English: Push aggregated result to frontend via SSE to provide a streaming response
                // Step 6⃣ SSE 推送执行结果给前端
                emitter.send(bundle);

                // 中文：显式完成事件流，释放连接资源
                // English: Explicitly complete the event stream to release connection resources
                // Step 7⃣ 完成推送并关闭连接
                emitter.complete();

            } catch (Exception e) {
                // 中文：捕获所有异常并以错误事件通知客户端，保证协议一致性
                // English: Catch all exceptions and notify client via error event to preserve protocol consistency
                // Step 8⃣ 异常处理，推送错误并结束 SSE
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("执行异常：" + e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping(value = "/execute1")
    /**
     * 方法说明 / Method Description:
     * 中文：示例性入口，异步触发策略执行与Kafka发布，但直接返回空结果用于占位或联调。
     * English: Sample entry that asynchronously triggers strategy execution and Kafka publish, returning null for placeholder or integration testing.
     *
     * 参数 / Parameters:
     * @param req 中文说明：策略执行请求体 / English: Strategy execution request body
     *
     * 返回值 / Return:
     * 中文：StrategyResultBundle（此处返回null，表示无需同步返回结果） / English: StrategyResultBundle (returns null here, indicating no synchronous result)
     *
     * 异常 / Exceptions:
     * 中文：内部异常通过日志记录，不向调用方抛出；用于演示异步触发场景 / English: Internal exceptions logged without throwing to caller; used to demonstrate async triggering scenario.
     */
    public StrategyResultBundle execute1(@RequestBody StrategyRequest req) {
        // 中文：异步执行以避免阻塞，提高接口吞吐
        // English: Execute asynchronously to avoid blocking and increase endpoint throughput
        // Step 2⃣ 异步执行任务，防止 Controller 阻塞
        CompletableFuture.runAsync(() -> {
            try {
                // 中文：构建策略上下文以传递必要的领域信息
                // English: Build strategy context to carry necessary domain information
                // Step 3⃣ 构建策略上下文对象（封装调用环境）
                StrategyContext ctx = StrategyContext.builder()
                        .userId(req.getUserId())
                        .symbol(req.getSymbol())
                        .extra(req.getExtra())
                        .requestTime(Instant.now())
                        .build();

                // 中文：调用外观执行策略集合，内部含并行与锁控
                // English: Invoke facade to execute strategy set with internal parallelism and locking
                // Step 4⃣ 调用 Facade 执行策略组合（内部含锁/并发控制）
                StrategyResultBundle bundle = engine.executeAll(req.getUserId(), req.getStrategyIds(), ctx);

                // 中文：异步发布结果到Kafka，便于下游消费
                // English: Asynchronously publish results to Kafka for downstream consumption
                // Step 5⃣ 异步发布 Kafka 消息（非阻塞）
                kafkaPublisher.publish("quant-strategy-result", bundle);

            } catch (Exception e) {
                // 中文：统一记录异常便于问题定位与审计
                // English: Log exceptions uniformly for troubleshooting and auditing
                log.error("执行异常：{}|Log_message", e.getMessage(), e);
            }
        });
        return null;
    }
}
