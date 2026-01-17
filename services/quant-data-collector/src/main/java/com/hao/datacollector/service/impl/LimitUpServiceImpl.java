package com.hao.datacollector.service.impl;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.common.utils.HttpUtil;
import com.hao.datacollector.dal.dao.LimitUpMapper;
import com.hao.datacollector.dto.param.limitup.LimitUpStockQueryParam;
import com.hao.datacollector.dto.table.limitup.LimitUpStockInfoInsertDTO;
import com.hao.datacollector.dto.table.limitup.LimitUpStockTopicRelationInsertDTO;
import com.hao.datacollector.dto.table.limitup.LimitUpStockTradeDTO;
import com.hao.datacollector.dto.table.topic.BaseTopicInsertDTO;
import com.hao.datacollector.properties.DataCollectorProperties;
import com.hao.datacollector.service.LimitUpService;
import com.hao.datacollector.web.vo.limitup.*;
import constants.DataSourceConstants;
import constants.DateTimeFormatConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import util.DateUtil;
import util.PageUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 涨停数据的采集与转档实现，负责从 Wind 接口抓取涨停详情并拆分落库。
 * <p>
 * 核心步骤：校验交易日 → 调用远端接口获取 JSON → 解析为响应对象 →
 * 拆分成多表 DTO 并批量写入，同时提供查询接口为前端使用。
 * </p>
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-06-12 19:34:31
 * @description: 涨停相关接口实现类
 */
/**
 * 实现思路：
 * <p>
 * 1. 对接 Wind 接口获取指定日期的涨停数据，并进行格式化和有效性校验。
 * 2. 将原始数据拆分成基础题材、关联关系、个股三类 DTO，完成去重后批量写入数据库。
 * 3. 通过 Mapper 封装的查询/统计能力，提供列表检索、分页以及多维度分析结果。
 */
@Slf4j
@Service
public class LimitUpServiceImpl implements LimitUpService {

    @Autowired
    private LimitUpMapper limitUpMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wind_base.limit_up.url}") // Corrected @Value annotation
    private String limitUpBaseUrl;

    @Autowired
    private DataCollectorProperties properties;

    /**
     * 获取解析后的涨停选股接口数据
     *
     * @param tradeTime 交易日期
     * @return 解析结果对象
     */
    @Override
    public ApiResponse<ResultObjectVO> getLimitUpData(String tradeTime) { // Changed return type
        try {
            if (!StringUtils.hasLength(tradeTime)) {
                tradeTime = DateUtil.getCurrentDateTime(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
            }
            //非交易日无数据。
            // Corrected DateCache usage if it was incorrect
            if (!DateCache.ThisYearTradeDateList.contains(DateUtil.parseToLocalDate(tradeTime, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT))) {
                log.warn("非交易日|Not_a_trade_date,tradeTime={}", tradeTime);
                throw new RuntimeException("LimitUpServiceImpl_getLimitUpData: " + tradeTime + " is not a trade date.");
            }
            //url具体参数含义可查看TopicDetailParam,日期格式必须类似20250609
            String url = String.format(limitUpBaseUrl, tradeTime);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(DataSourceConstants.WIND_SESSION_NAME, properties.getWindSessionId());
            // 调用 Wind 接口获取涨停原始 JSON 字符串
            String response = HttpUtil.sendGetRequest(DataSourceConstants.WIND_PROD_WGQ + url, headers, 10000, 30000).getBody();
            if (!StringUtils.hasLength(response)) {
                log.warn("日志记录|Log_message,LimitUpServiceImpl_getLimitUpData:_HTTP_response_body_is_empty_for_tradeTime:_{}", tradeTime);
                throw new RuntimeException("LimitUpServiceImpl_getLimitUpData: HTTP response body is empty for tradeTime: " + tradeTime);
            }
            // 配置忽略未知字段，避免反序列化错误。解析JSON响应为ApiResponse对象
            objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // 通过泛型类型工厂将响应结构映射为 ApiResponse<ResultObjectVO>
            ApiResponse<ResultObjectVO> result = objectMapper.readValue(
                    response,
                    objectMapper.getTypeFactory().constructParametricType(
                            ApiResponse.class,
                            ResultObjectVO.class
                    )
            );
            log.info("日志记录|Log_message,LimitUpServiceImpl_getLimitUpData,resultCode={}", result.getResultCode());
            if (result == null || !"200".equals(result.getResultCode()) || result.getResultObject() == null || result.getResultObject().getStockDetail() == null || result.getResultObject().getStockDetail().isEmpty()) {
                throw new RuntimeException("获取涨停数据为空或接口返回错误，交易日期: {}，响应码: {}" + tradeTime + result != null ? result.getResultCode() : "null");
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get or parse limit up data:" + e.getMessage());
        }
    }

    /**
     * 涨停数据转档
     *
     * @param tradeTime 交易日期
     * @return 操作结果
     */
    @Override
    public Boolean transferLimitUpDataToDatabase(String tradeTime) {
        //如果tradeTime为空，则使用当前时间
        if (!StringUtils.hasLength(tradeTime)) {
            tradeTime = DateUtil.getCurrentDateTime(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        }
        try {
            log.info("开始执行涨停数据转档任务，交易日期:_{}|Log_message", tradeTime);
            // 获取涨停数据
            ApiResponse<ResultObjectVO> limitUpResponse = getLimitUpData(tradeTime);
            // 获取详情数据,进行分表转换
            ResultObjectVO resultData = limitUpResponse.getResultObject();
            List<TopicStockVO> stockDetails = resultData.getStockDetail();
            log.info("获取到_{}_条涨停股票数据，开始转档到数据库|Log_message", stockDetails.size());
            List<LimitUpStockInfoInsertDTO> limitUpStockInfoList = new ArrayList<>();
            //对应基础标签表
            List<BaseTopicInsertDTO> baseTopicInsertList = new ArrayList<>();
            List<LimitUpStockTopicRelationInsertDTO> relationInsertList = new ArrayList<>();
            LocalDate localTradeTime = DateUtil.parseToLocalDate(tradeTime, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
            for (TopicStockVO stockDetail : stockDetails) {
                for (TopicInfoVO stockDetailTopic : stockDetail.getTopics()) {
                    if (stockDetailTopic == null) {
                        log.warn("股票详情题材为空|Stock_detail_topic_is_null,tradeDate={},windCode={}", localTradeTime, stockDetail.getWindCode());
                        continue;
                    }
                    //基础标签
                    BaseTopicInsertDTO baseTopicInsertDTO = new BaseTopicInsertDTO();
                    baseTopicInsertDTO.setTopicId(stockDetailTopic.getTopicId());
                    baseTopicInsertDTO.setTopicName(stockDetailTopic.getTopic());
                    baseTopicInsertList.add(baseTopicInsertDTO);
                    //关联标签
                    LimitUpStockTopicRelationInsertDTO relationInsertDTO = new LimitUpStockTopicRelationInsertDTO();
                    relationInsertDTO.setWindCode(stockDetail.getWindCode());
                    relationInsertDTO.setTradeDate(localTradeTime);
                    BeanUtils.copyProperties(stockDetailTopic, relationInsertDTO);
                    relationInsertList.add(relationInsertDTO);
                }
                LimitUpStockInfoInsertDTO stockInfoInsertDTO = new LimitUpStockInfoInsertDTO();
                // 股票自身的成交、资金等指标汇总到主表 DTO
                BeanUtils.copyProperties(stockDetail, stockInfoInsertDTO);
                stockInfoInsertDTO.setTradeDate(localTradeTime);
                stockInfoInsertDTO.setWindName(stockDetail.getName());
                stockInfoInsertDTO.setLimitStatus(stockDetail.getStatus());
                stockInfoInsertDTO.setVolumeNetIn(stockDetail.getVolumeNetin());
                stockInfoInsertDTO.setMainForcesNtimes(stockDetail.getMainForcesBtimes());
                limitUpStockInfoList.add(stockInfoInsertDTO);
            }
            // 先删除当天旧数据
            log.info("开始删除当前交易日_{}_的旧数据|Log_message", tradeTime);
            limitUpMapper.deleteLimitUpStockInfoByTradeDate(tradeTime);
            limitUpMapper.deleteStockTopicRelationByTradeDate(tradeTime);
            log.info("删除交易日_{}_的旧数据完成|Log_message", tradeTime);
            // 批量插入新数据
            log.info("开始批量插入新数据|Log_message");
            if (!baseTopicInsertList.isEmpty()) {
                //去重后的结果
                List<BaseTopicInsertDTO> distinctList = baseTopicInsertList.stream()
                        .filter(dto -> dto.getTopicId() != null) // 防止 NullPointerException
                        .collect(Collectors.toMap(
                                BaseTopicInsertDTO::getTopicId, // 以 topicId 为 key
                                dto -> dto,                     // value 保留原对象
                                (existing, replacement) -> existing // 若重复，保留第一个
                        ))
                        .values()
                        .stream()
                        .collect(Collectors.toList());
                for (BaseTopicInsertDTO topicInsertDTO : distinctList) {
                    // BaseTopic 逐条 upsert，避免重复主键导致批量失败
                    Boolean insertBaseTopicResult = limitUpMapper.insertBaseTopic(topicInsertDTO);
                    if (log.isDebugEnabled()) {
                        log.debug("插入BaseTopic结果|Insert_base_topic_result,topicId={},result={}", topicInsertDTO.getTopicId(), insertBaseTopicResult);
                    }
                }
                log.info("插入BaseTopic={}条", baseTopicInsertList.size());
            }
            if (!relationInsertList.isEmpty()) {
                // 股票与题材的关联采用批量插入，维持性能
                limitUpMapper.batchInsertStockTopicRelation(relationInsertList);
                log.info("插入relationInsertList={}条", relationInsertList.size());
            }
            if (!limitUpStockInfoList.isEmpty()) {
                // 主表信息一次性写入，供后续查询接口使用
                limitUpMapper.batchInsertLimitUpStockInfo(limitUpStockInfoList);
                log.info("插入limitUpStockInfoList={}_条", limitUpStockInfoList.size());
            }
            log.info("批量插入新数据完成|Log_message");
            log.info("涨停数据转档成功，交易日期={}，共处理={}条记录|Log_message", tradeTime, stockDetails.size());
            return true;
        } catch (Exception e) {
            log.error("涨停数据转档失败，交易日期:_{}|Log_message", tradeTime, e);
            return false;
        }
    }

    /**
     * 查询涨停股票信息列表
     *
     * @param queryParam 查询参数
     * @return 结果列表
     */
    @Override
    public List<LimitUpStockQueryResultVO> queryLimitUpStockList(LimitUpStockQueryParam queryParam) {
        log.info("日志记录|Log_message,LimitUpServiceImpl_queryLimitUpStockList_start=queryParam={}", JSON.toJSONString(queryParam));
        // 处理分页参数，将pageNo转换为offset
        if (queryParam.getPageNo() != null && queryParam.getPageSize() != null) {
            int offset = PageUtil.calculateOffset(queryParam.getPageNo(), queryParam.getPageSize());
            queryParam.setPageNo(offset);
        }
        // Mapper 负责根据筛选条件返回指定页的数据
        List<LimitUpStockQueryResultVO> result = limitUpMapper.queryLimitUpStockList(queryParam);
        log.info("日志记录|Log_message,LimitUpServiceImpl_queryLimitUpStockList_success=result_count={}", result.size());
        return result;
    }

    /**
     * 获取交易日涨停股票代码列表
     *
     * @param tradeDateStart 交易日期开始
     * @param tradeDateEnd   交易日期结束
     * @return 交易日涨停股票代码列表
     */
    @Override
    public Map<String, Set<String>> getLimitUpTradeDateMap(String tradeDateStart, String tradeDateEnd) {
        List<LimitUpStockTradeDTO> stockByTradeDateList = limitUpMapper.getLimitCodeByTradeDate(tradeDateStart, tradeDateEnd);
        // 以交易日为 key 聚合涨停股票代码，TreeMap 保证按日期顺序输出
        return stockByTradeDateList.stream()
                .collect(Collectors.groupingBy(
                        vo -> DateUtil.formatLocalDate(vo.getTradeDate(), DateTimeFormatConstants.COMPACT_DATE_FORMAT),
                        //TreeMap 基于红黑树实现，插入和查找为稳定的 O(logN)，在 key 数量为几千以内时仅需十几次比较即可完成排序结构维护，性能影响极小，
                        //反而比 HashMap,O(1) 均摊，最坏情况 O(N)（哈希冲突严重时） 后续手动排序更高效，是一种在小规模有序需求场景下结构与性能的最佳平衡方案。
                        TreeMap::new, //指定使用 TreeMap（自动按 key 排序）
                        Collectors.mapping(LimitUpStockTradeDTO::getWindCode, Collectors.toSet())
                ));
    }

    /**获取转档涨停数据最大日期
     *
     * @return 最大日期 yyyyMMdd
     */
    @Override
    public String getTransferLimitUpDataMaxDate() {
        return limitUpMapper.getTransferLimitUpDataMaxDate();
    }
}
