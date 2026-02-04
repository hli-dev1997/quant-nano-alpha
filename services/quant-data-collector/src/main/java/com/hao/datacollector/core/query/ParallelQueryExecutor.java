package com.hao.datacollector.core.query;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 并行查询执行器
 * <p>
 * 职责：接收查询计划（QuerySegment 列表），并行执行多表查询，合并结果。
 * <p>
 * 设计优势：
 * <ul>
 *   <li>通用化：支持任意类型的查询结果</li>
 *   <li>并行化：使用 CompletableFuture 并行执行多表查询</li>
 *   <li>解耦：查询逻辑通过 BiFunction 传入，执行器只负责调度</li>
 * </ul>
 *
 * @author hli
 * @date 2026-02-04
 */
@Slf4j
@Component
public class ParallelQueryExecutor {

    @Resource(name = "ioTaskExecutor")
    private Executor ioTaskExecutor;

    /**
     * 执行并行查询
     * <p>
     * 根据查询分段列表，并行执行查询动作，合并结果。
     *
     * @param segments    查询分段列表
     * @param queryAction 具体的查询动作（接收 QuerySegment 和 stockList，返回查询结果）
     * @param stockList   股票代码列表
     * @param <T>         返回结果类型
     * @return 合并后的结果列表
     */
    public <T> List<T> executeParallel(
            List<QuerySegment> segments,
            BiFunction<QuerySegment, List<String>, List<T>> queryAction,
            List<String> stockList) {

        if (segments == null || segments.isEmpty()) {
            log.debug("查询分段为空，返回空列表|Empty_segments_return_empty_list");
            return Collections.emptyList();
        }

        // 单表查询，直接执行（无需并行）
        if (segments.size() == 1) {
            QuerySegment segment = segments.get(0);
            log.info("单表查询|Single_table_query,table={},range={}-{}",
                    segment.getTableType(), segment.getStartDate(), segment.getEndDate());
            return queryAction.apply(segment, stockList);
        }

        // 多表并行查询
        log.info("并行查询开始|Parallel_query_start,segmentCount={}", segments.size());

        List<CompletableFuture<List<T>>> futures = segments.stream()
                .map(segment -> CompletableFuture.supplyAsync(
                        () -> {
                            log.info("查询分段|Query_segment,table={},range={}-{}",
                                    segment.getTableType(), segment.getStartDate(), segment.getEndDate());
                            return queryAction.apply(segment, stockList);
                        },
                        ioTaskExecutor
                ))
                .collect(Collectors.toList());

        // 等待所有查询完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 合并结果（保持顺序：WARM 在前，HOT 在后）
        List<T> combinedResult = new ArrayList<>();
        for (CompletableFuture<List<T>> future : futures) {
            try {
                List<T> result = future.get();
                if (result != null) {
                    combinedResult.addAll(result);
                }
            } catch (Exception e) {
                log.error("并行查询分段失败|Parallel_query_segment_failed", e);
            }
        }

        log.info("并行查询完成|Parallel_query_done,totalCount={}", combinedResult.size());
        return combinedResult;
    }
}
