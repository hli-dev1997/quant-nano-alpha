package com.hao.datacollector.core.query;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 冷热表路由器
 * <p>
 * 职责：根据时间范围判断需要查询哪些表，返回查询计划（QuerySegment 列表）。
 * <p>
 * 路由规则：
 * <ul>
 *   <li>2024-01-01 及之后的数据 → HOT 表</li>
 *   <li>2024-01-01 之前的数据 → WARM 表</li>
 *   <li>跨越边界的查询 → 返回两个分段</li>
 * </ul>
 *
 * @author hli
 * @date 2026-02-04
 */
@Slf4j
@Component
public class TableRouter {

    /**
     * 热表起始日期（2024-01-01 及之后的数据存热表）
     */
    private static final LocalDate HOT_DATA_START_DATE = LocalDate.of(2024, 1, 1);

    /**
     * 根据时间范围生成查询计划
     * <p>
     * 判断查询时间范围是否跨越冷热表边界，返回对应的查询分段列表。
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 查询计划列表（可能包含 1-2 个分段）
     */
    public List<QuerySegment> route(LocalDate startDate, LocalDate endDate) {
        List<QuerySegment> segments = new ArrayList<>(2);

        // 判断查询范围是否涉及温表和热表
        boolean queryWarm = startDate.isBefore(HOT_DATA_START_DATE);
        boolean queryHot = !endDate.isBefore(HOT_DATA_START_DATE);

        // 温表分段
        if (queryWarm) {
            LocalDate warmEnd = queryHot
                    ? HOT_DATA_START_DATE.minusDays(1)
                    : endDate;
            segments.add(new QuerySegment(TableType.WARM, startDate, warmEnd));
            log.debug("路由温表|Route_warm_table,range={}-{}", startDate, warmEnd);
        }

        // 热表分段
        if (queryHot) {
            LocalDate hotStart = queryWarm
                    ? HOT_DATA_START_DATE
                    : startDate;
            segments.add(new QuerySegment(TableType.HOT, hotStart, endDate));
            log.debug("路由热表|Route_hot_table,range={}-{}", hotStart, endDate);
        }

        log.info("表路由完成|Table_route_done,inputRange={}-{},segmentCount={}",
                startDate, endDate, segments.size());

        return segments;
    }

    /**
     * 获取热表起始日期
     *
     * @return 热表起始日期
     */
    public LocalDate getHotDataStartDate() {
        return HOT_DATA_START_DATE;
    }
}
