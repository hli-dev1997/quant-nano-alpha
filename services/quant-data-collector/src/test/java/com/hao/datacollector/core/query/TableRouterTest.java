package com.hao.datacollector.core.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TableRouter 单元测试
 * <p>
 * 测试表路由器的分段逻辑，验证不同时间范围下的路由结果。
 *
 * @author hli
 * @date 2026-02-04
 */
class TableRouterTest {

    private TableRouter tableRouter;

    @BeforeEach
    void setUp() {
        tableRouter = new TableRouter();
    }

    @Test
    @DisplayName("热表边界日期应为2024-01-01")
    void getHotDataStartDate_shouldReturn20240101() {
        LocalDate hotStartDate = tableRouter.getHotDataStartDate();
        assertEquals(LocalDate.of(2024, 1, 1), hotStartDate);
    }

    @Test
    @DisplayName("仅查询热表：时间范围在2024-01-01及之后")
    void route_shouldReturnSingleHotSegment_whenDateRangeIsAfter2024() {
        LocalDate start = LocalDate.of(2024, 6, 1);
        LocalDate end = LocalDate.of(2024, 6, 30);

        List<QuerySegment> segments = tableRouter.route(start, end);

        assertEquals(1, segments.size());
        QuerySegment segment = segments.get(0);
        assertEquals(TableType.HOT, segment.getTableType());
        assertEquals(start, segment.getStartDate());
        assertEquals(end, segment.getEndDate());
    }

    @Test
    @DisplayName("仅查询温表：时间范围在2024-01-01之前")
    void route_shouldReturnSingleWarmSegment_whenDateRangeIsBefore2024() {
        LocalDate start = LocalDate.of(2023, 6, 1);
        LocalDate end = LocalDate.of(2023, 12, 31);

        List<QuerySegment> segments = tableRouter.route(start, end);

        assertEquals(1, segments.size());
        QuerySegment segment = segments.get(0);
        assertEquals(TableType.WARM, segment.getTableType());
        assertEquals(start, segment.getStartDate());
        assertEquals(end, segment.getEndDate());
    }

    @Test
    @DisplayName("跨表查询：时间范围跨越2024-01-01边界")
    void route_shouldReturnTwoSegments_whenDateRangeCrossesBoundary() {
        LocalDate start = LocalDate.of(2023, 12, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        List<QuerySegment> segments = tableRouter.route(start, end);

        assertEquals(2, segments.size());

        // 第一个分段：温表
        QuerySegment warmSegment = segments.get(0);
        assertEquals(TableType.WARM, warmSegment.getTableType());
        assertEquals(LocalDate.of(2023, 12, 1), warmSegment.getStartDate());
        assertEquals(LocalDate.of(2023, 12, 31), warmSegment.getEndDate());

        // 第二个分段：热表
        QuerySegment hotSegment = segments.get(1);
        assertEquals(TableType.HOT, hotSegment.getTableType());
        assertEquals(LocalDate.of(2024, 1, 1), hotSegment.getStartDate());
        assertEquals(LocalDate.of(2024, 1, 31), hotSegment.getEndDate());
    }

    @Test
    @DisplayName("边界日期测试：结束日期正好是2024-01-01")
    void route_shouldReturnHotSegment_whenEndDateIsExactlyBoundary() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 1);

        List<QuerySegment> segments = tableRouter.route(start, end);

        assertEquals(1, segments.size());
        assertEquals(TableType.HOT, segments.get(0).getTableType());
    }

    @Test
    @DisplayName("边界日期测试：开始日期是2023-12-31，结束日期是2024-01-01")
    void route_shouldReturnTwoSegments_whenRangeSpansOneDay() {
        LocalDate start = LocalDate.of(2023, 12, 31);
        LocalDate end = LocalDate.of(2024, 1, 1);

        List<QuerySegment> segments = tableRouter.route(start, end);

        assertEquals(2, segments.size());
        assertEquals(TableType.WARM, segments.get(0).getTableType());
        assertEquals(LocalDate.of(2023, 12, 31), segments.get(0).getStartDate());
        assertEquals(LocalDate.of(2023, 12, 31), segments.get(0).getEndDate());
        assertEquals(TableType.HOT, segments.get(1).getTableType());
        assertEquals(LocalDate.of(2024, 1, 1), segments.get(1).getStartDate());
    }
}
