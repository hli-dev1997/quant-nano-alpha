package com.hao.datacollector.web.controller;

import com.hao.datacollector.dal.dao.TopicMapper;
import com.hao.datacollector.dto.param.topic.TopicCategoryAndStockParam;
import com.hao.datacollector.dto.param.topic.TopicInfoParam;
import com.hao.datacollector.dto.param.topic.TopicStockQueryParam;
import com.hao.datacollector.dto.table.topic.TopicStockDTO;
import com.hao.datacollector.service.TopicService;
import com.hao.datacollector.web.vo.topic.TopicCategoryAndStockVO;
import com.hao.datacollector.web.vo.topic.TopicInfoKplVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import exception.BusinessException;

/**
 * @author Hao Li
 * @Date 2025-07-22 10:48:15
 * @description: 题材Controller
 */
@Slf4j
@Tag(name = "题材模块")
@RestController("topic")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @Autowired
    private TopicMapper topicMapper;

    @Operation(summary = "转档题材库", method = "POST")
    @Parameters({
            @Parameter(name = "endId", description = "转档结束题材id", required = true)
    })
    @PostMapping("/kpl_job")
    public Boolean setKplTopicInfoJob(@RequestBody Integer endId) {
        //获取表中最大topicId作为起始id
        Integer maxId = topicMapper.getKplTopicMaxId();
        if (maxId == null) {
            maxId = 1;
        }
        if (endId <= maxId) {
            throw new BusinessException(4004, "转档endId不能<=表中存储最大id|Transfer_endId_cannot_be_less_than_maxId");
        }
        return topicService.setKplTopicInfoJob(maxId, endId);
    }

    @GetMapping("topic_list")
    @Operation(summary = "获取题材信息列表", description = "支持多条件筛选查询题材信息")
    @Parameters({
            @Parameter(name = "topicId", description = "题材ID"),
            @Parameter(name = "name", description = "题材名称（模糊查询）"),
            @Parameter(name = "classLayer", description = "分类层级"),
            @Parameter(name = "plateSwitch", description = "板块开关"),
            @Parameter(name = "stkSwitch", description = "股票开关"),
            @Parameter(name = "isNew", description = "是否新增：1.是，0.否"),
            @Parameter(name = "minPower", description = "最小权重"),
            @Parameter(name = "maxPower", description = "最大权重"),
            @Parameter(name = "minSubscribe", description = "最小订阅数"),
            @Parameter(name = "minGoodNum", description = "最小点赞数"),
            @Parameter(name = "status", description = "状态：0.无效，1.有效"),
            @Parameter(name = "pageNo", description = "页码", example = "1"),
            @Parameter(name = "pageSize", description = "每页大小", example = "10")
    })
    public List<TopicInfoKplVO> getKplTopicInfoList(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String classLayer,
            @RequestParam(required = false) String plateSwitch,
            @RequestParam(required = false) String stkSwitch,
            @RequestParam(required = false) Integer isNew,
            @RequestParam(required = false) Integer minPower,
            @RequestParam(required = false) Integer maxPower,
            @RequestParam(required = false) Integer minSubscribe,
            @RequestParam(required = false) Integer minGoodNum,
            @RequestParam(required = false, defaultValue = "1") Integer status,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        TopicInfoParam queryDTO = TopicInfoParam.builder()
                .topicId(topicId)
                .name(name)
                .classLayer(classLayer)
                .plateSwitch(plateSwitch)
                .stkSwitch(stkSwitch)
                .isNew(isNew)
                .minPower(minPower)
                .maxPower(maxPower)
                .minSubscribe(minSubscribe)
                .minGoodNum(minGoodNum)
                .status(status)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
        return topicService.getKplTopicInfoList(queryDTO);
    }

    @GetMapping("category_stock_list")
    @Operation(
            summary = "获取类别及关联股票列表",
            description = "查询题材类别信息及其关联的股票映射数据，支持分页、股票、分类、题材等多条件筛选"
    )
    @Parameters({
            @Parameter(name = "topicId", description = "所属题材ID", example = "22"),
            @Parameter(name = "topicName", description = "题材名称（模糊）", example = "BC电池"),

            @Parameter(name = "windCode", description = "股票代码（精确）", example = "300537.SH"),
            @Parameter(name = "windName", description = "股票名称（模糊）", example = "广信材料"),

            @Parameter(name = "categoryId", description = "类别ID", example = "1001"),
            @Parameter(name = "categoryName", description = "分类名称（模糊）", example = "材料"),
            @Parameter(name = "parentCategoryId", description = "父类别ID", example = "1534"),
            @Parameter(name = "categoryZsCode", description = "类别表指数代码", example = "880880"),
            @Parameter(name = "pageNo", description = "页码", example = "1"),
            @Parameter(name = "pageSize", description = "每页大小", example = "10")
    })
    public List<TopicCategoryAndStockVO> getKplCategoryAndStockList(
            @RequestParam(required = false) Integer topicId,
            @RequestParam(required = false) String topicName,
            @RequestParam(required = false) String windCode,
            @RequestParam(required = false) String windName,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer parentCategoryId,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String categoryZsCode,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        TopicCategoryAndStockParam queryDTO = TopicCategoryAndStockParam.builder()
                .windCode(windCode)
                .windName(windName)
                .topicId(topicId)
                .topicName(topicName)
                .categoryId(categoryId)
                .categoryName(categoryName)
                .parentCategoryId(parentCategoryId)
                .categoryZsCode(categoryZsCode)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .build();
        return topicService.getKplCategoryAndStockList(queryDTO);
    }

    @GetMapping("topic_stock_list")
    @Operation(
            summary = "获取题材及其映射股票列表",
            description = "支持通过题材ID、名称、股票代码、分类信息等筛选映射关系,key = topicId,value = 股票代码列表"
    )
    @Parameters({
            @Parameter(name = "topicId", description = "所属题材ID", example = "22")
    })
    public Map<Integer, List<TopicStockDTO>> getTopicAndStockList(@RequestParam(required = false) Integer topicId) {
        TopicStockQueryParam query = TopicStockQueryParam.builder()
                .topicId(topicId)
                .build();
        return topicService.getKplTopicAndStockList(query);
    }
}