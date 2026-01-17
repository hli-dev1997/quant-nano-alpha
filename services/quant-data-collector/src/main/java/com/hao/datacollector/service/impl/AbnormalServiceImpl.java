package com.hao.datacollector.service.impl;


import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.parser.Feature;
import com.hao.datacollector.common.utils.ExtremeValueUtil;
import com.hao.datacollector.common.utils.HttpUtil;
import com.hao.datacollector.dal.dao.AbnormalMapper;
import com.hao.datacollector.dto.param.abnormal.IndexSourceParam;
import com.hao.datacollector.properties.DataCollectorProperties;
import com.hao.datacollector.service.AbnormalService;
import com.hao.datacollector.web.vo.abnormal.AbnormalIndexVO;
import com.hao.datacollector.web.vo.abnormal.ActiveRankRecordVO;
import com.hao.datacollector.web.vo.abnormal.ActiveSeatsRankVO;
import constants.CommonConstants;
import constants.DataSourceConstants;
import constants.DateTimeFormatConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import util.DateUtil;
import exception.BusinessException;
import exception.DataException;
import exception.ExternalServiceException;

import java.util.List;

/**
 * 龙虎榜相关数据的编排实现，负责从 Wind 接口取数、解析并落库。
 * <p>
 * 统一的实现思路是：准备访问参数 → 调用 {@link HttpUtil} 获取原始 JSON →
 * 校验响应并映射为 VO → 视情况写入数据库或直接返回，整个过程兼顾幂等与日志。
 * </p>
 *
 * @author hli
 * @Date 2025-06-23 14:16:42
 * @description: 龙虎榜实现类
 */
/**
 * 实现思路：
 * <p>
 * 1. 封装 Wind 龙虎榜相关接口的请求参数与认证信息，通过 HttpUtil 拉取源数据。
 * 2. 将返回的 JSON 数据解析为 VO 列表，必要时进行极值处理或日期兜底。
 * 3. 借助 Mapper 层实现批量入库，形成龙虎榜首页、席位榜、活跃榜等多维数据的转储能力。
 */
@Slf4j
@Service
public class AbnormalServiceImpl implements AbnormalService {
    @Autowired
    private DataCollectorProperties properties;

    @Value("${wind_base.abnormal.home_page.url}")
    private String homePageUrl;

    @Value("${wind_base.abnormal.seats.url}")
    private String seatsUrl;

    @Value("${wind_base.abnormal.active.url}")
    private String activeUrl;

    @Autowired
    private AbnormalMapper abnormalMapper;

    /**
     * 获取source龙虎榜首页
     *
     * @param tradeDate 交易日期
     * @param sortCol   排序字段
     * @param orderType 排序类型
     * @return 首页VO
     * List<AbnormalIndexVO>
     */
    @Override
    public List<AbnormalIndexVO> getSourceHomePage(String tradeDate, Integer sortCol, Integer orderType) {
        if (StringUtils.isEmpty(tradeDate)) {
            tradeDate = DateUtil.getCurrentDateTimeByStr(DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        }
        // 判断当前传入日期是否属交易日，预留扩展点给未来的交易日校验
        if (false) {
            throw new BusinessException(4005, "不支持的交易日|Not_supported_trade_date");
        }
        HttpHeaders httpHeader = new HttpHeaders();
        httpHeader.set(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        // Wind 接口统一走生产域名，因此统一拼接基地址与配置化路径
        String url = String.format(DataSourceConstants.WIND_PROD_WGQ + homePageUrl);
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        // 组装排序与日期参数，严格对齐外部接口的字段命名
        queryParams.add("tradeDate", tradeDate);
        queryParams.add("sortCol", sortCol.toString());
        queryParams.add("orderType", orderType.toString());
        ResponseEntity<String> entity = null;
        try {
            // 所有网络访问统一走 HttpUtil，便于集中处理超时与异常
            entity = HttpUtil.sendGetWithParams(url, queryParams, httpHeader, 10000, 10000);
        } catch (Exception e) {
            throw new ExternalServiceException("获取龙虎榜首页失败|Get_home_page_failed," + e.getMessage(), e);
        }
        if (!CommonConstants.SUCCESS_FLAG.equals(entity.getStatusCode().toString())) {
            throw new ExternalServiceException("获取龙虎榜首页失败|Get_home_page_failed,statusCode=" + entity.getStatusCode());
        }
        // 使用 Fastjson 将数组 JSON 反序列化为 VO，保持字段顺序不变
        List<AbnormalIndexVO> indexVOList = JSONObject.parseObject(entity.getBody().toString(), new TypeReference<List<AbnormalIndexVO>>() {
        }, Feature.OrderedField);
        log.info("日志记录|Log_message,getSourceHomePage_indexVOList.size={}", indexVOList.size());
        return indexVOList;

    }

    /**
     * 转档首页数据源
     *
     * @param indexSourceParam 首页数据源参数对象
     * @return 操作结果
     */
    @Override
    public Boolean transferHomePage(IndexSourceParam indexSourceParam) {
        List<AbnormalIndexVO> indexVOList = getSourceHomePage(indexSourceParam.getTradeDate(), indexSourceParam.getSortCol(), indexSourceParam.getOrderType());
        if (indexVOList.isEmpty()) {
            throw new DataException("转档首页数据为空|Transfer_home_page_data_empty,param=" + JSONObject.toJSONString(indexSourceParam));
        }
        // 调用 Mapper 做批量写入，依赖数据库层面的去重保障幂等
        int resultCount = abnormalMapper.insertHomePageSourceData(indexVOList, indexSourceParam.getTradeDate());
        log.info("日志记录|Log_message,transferHomePage_countResult={}", resultCount);
        return resultCount > 0;
    }

    /**
     * 获取龙虎榜席位榜数据源
     *
     * @param period   时间周期类型,0-近1月，1-近3月，2-近半年，3-今年以来
     * @param seatType 席位类型,全部席位：0，高胜率席位介入：1，机构介入：2，游资介入：3，普通介入：4
     * @param pageNo   当前页码
     * @param pageSize 每页记录数
     * @param sortCol  排序字段,0-上榜次数、1-跟投胜率、2-总成交额、3-买入次数、4-卖出次数、5-席位类型
     * @param sortFlag 排序标记,0-不排序，1-排升序，-1排降序
     * @return 席位榜数据列表
     */
    @Override
    public List<ActiveSeatsRankVO> getSourceListOfSeats(Integer period, Integer seatType, Integer pageNo, Integer pageSize, Integer sortCol, Integer sortFlag) {
        // 判断当前传入日期是否属交易日，可与首页逻辑保持一致
        if (false) {
            throw new BusinessException(4005, "不支持的交易日|Not_supported_trade_date");
        }
        HttpHeaders httpHeader = new HttpHeaders();
        httpHeader.set(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        // 参数保持与 Wind 席位榜接口字段一致，方便排查问题
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("period", period.toString());
        queryParams.add("seatType", seatType.toString());
        queryParams.add("pageNo", pageNo.toString());
        queryParams.add("pageSize", pageSize.toString());
        queryParams.add("sortCol", sortCol.toString());
        queryParams.add("sortFlag", sortFlag.toString());
        ResponseEntity<String> entity = null;
        try {
            // 与首页相同的 GET 调用模式，只是命中不同的后端路径
            entity = HttpUtil.sendGetWithParams(DataSourceConstants.WIND_PROD_WGQ + seatsUrl, queryParams, httpHeader, 10000, 10000);
        } catch (Exception e) {
            throw new ExternalServiceException("获取龙虎榜席位榜失败|Get_seats_list_failed," + e.getMessage(), e);
        }
        if (!CommonConstants.SUCCESS_FLAG.equals(entity.getStatusCode().toString())) {
            throw new ExternalServiceException("获取龙虎榜席位榜失败|Get_seats_list_failed,statusCode=" + entity.getStatusCode());
        }
        // 将 JSON 直接转换为 VO 列表，供后续转档或透传使用
        List<ActiveSeatsRankVO> activeSeatsRankList = JSONObject.parseObject(entity.getBody().toString(), new TypeReference<List<ActiveSeatsRankVO>>() {
        }, Feature.OrderedField);
        log.info("日志记录|Log_message,getSourceListOfSeats_activeSeatsRankList.size={}", activeSeatsRankList.size());
        return activeSeatsRankList;
    }

    /**
     * 转档龙虎榜席位榜数据源
     *
     * @param period   时间周期类型,0-近1月，1-近3月，2-近半年，3-今年以来
     * @param seatType 席位类型,全部席位：0，高胜率席位介入：1，机构介入：2，游资介入：3，普通介入：4
     * @param pageNo   当前页码
     * @param pageSize 每页记录数
     * @param sortCol  排序字段,0-上榜次数、1-跟投胜率、2-总成交额、3-买入次数、4-卖出次数、5-席位类型
     * @param sortFlag 排序标记,0-不排序，1-排升序，-1排降序
     * @return 操作结果
     */
    public Boolean transferListOfSeats(Integer period, Integer seatType, Integer pageNo, Integer pageSize, Integer sortCol, Integer sortFlag) {
        List<ActiveSeatsRankVO> sourceListOfSeatList = getSourceListOfSeats(period, seatType, pageNo, pageSize, sortCol, sortFlag);
        //todo 极值处理bug(非极值也处理默认值)待修复
        ExtremeValueUtil.handleExtremeValues(sourceListOfSeatList);
        // DAO 内部完成批量 insert/replace，调用层只需判断写入结果
        int result = abnormalMapper.insertSourceListOfSeats(sourceListOfSeatList);
        return result > 0;
    }

    /**
     * 获取龙虎榜活跃榜数据源
     *
     * @param period   时间周期类型,0-近1月，1-近3月，2-近半年，3-今年以来
     * @param pageNo   当前页码
     * @param pageSize 每页记录数
     * @param sortCol  排序字段,0-上榜次数、1-跟投胜率、2-总成交额、3-买入次数、4-卖出次数、5-席位类型、6-涨跌幅
     * @param sortFlag 排序标记,0-不排序，1-排升序，-1排降序
     * @return 活跃榜数据列表
     */
    @Override
    public List<ActiveRankRecordVO> getSourceActiveRank(Integer period, Integer pageNo, Integer pageSize, Integer sortCol, Integer sortFlag) {
        // 判断当前传入日期是否属交易日，与其他接口保持一致的扩展点
        if (false) {
            throw new BusinessException(4005, "不支持的交易日|Not_supported_trade_date");
        }
        HttpHeaders httpHeader = new HttpHeaders();
        httpHeader.set(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        //param
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        // 活跃榜查询不区分席位类型，因此仅透传时间窗口与排序规则
        queryParams.add("period", period.toString());
        queryParams.add("pageNo", pageNo.toString());
        queryParams.add("pageSize", pageSize.toString());
        queryParams.add("sortCol", sortCol.toString());
        queryParams.add("sortFlag", sortFlag.toString());
        ResponseEntity<String> entity = null;
        try {
            // 直接请求活跃榜接口，复用统一的超时配置
            entity = HttpUtil.sendGetWithParams(DataSourceConstants.WIND_PROD_WGQ + activeUrl, queryParams, httpHeader, 10000, 10000);
        } catch (Exception e) {
            throw new ExternalServiceException("获取龙虎榜活跃榜失败|Get_active_rank_failed," + e.getMessage(), e);
        }
        if (!CommonConstants.SUCCESS_FLAG.equals(entity.getStatusCode().toString())) {
            throw new ExternalServiceException("获取龙虎榜活跃榜失败|Get_active_rank_failed,statusCode=" + entity.getStatusCode());
        }
        // Fastjson 反序列化列表数据，供后续落库或直接返回
        List<ActiveRankRecordVO> activeRankRecordVOList = JSONObject.parseObject(entity.getBody().toString(), new TypeReference<List<ActiveRankRecordVO>>() {
        }, Feature.OrderedField);
        log.info("日志记录|Log_message,getSourceActiveRank_activeRankRecordVOList.size={}", activeRankRecordVOList.size());
        return activeRankRecordVOList;
    }

    /**
     * 转档龙虎榜活跃榜数据源
     *
     * @param period   时间周期类型,0-近1月，1-近3月，2-近半年，3-今年以来
     * @param pageNo   当前页码
     * @param pageSize 每页记录数
     * @param sortCol  排序字段,0-上榜次数、1-跟投胜率、2-总成交额、3-买入次数、4-卖出次数、5-席位类型、6-涨跌幅
     * @param sortFlag 排序标记,0-不排序，1-排升序，-1排降序
     * @return 操作结果
     */
    @Override
    public Boolean transferActiveRank(Integer period, Integer pageNo, Integer pageSize, Integer sortCol, Integer sortFlag) {
        List<ActiveRankRecordVO> activeRankVOList = getSourceActiveRank(period, pageNo, pageSize, sortCol, sortFlag);
        //todo 极值处理bug(非极值也处理默认值)待修复
        ExtremeValueUtil.handleExtremeValues(activeRankVOList);
        // 调度 DAO 层做批量落库，结果用于告知调用端是否成功
        int result = abnormalMapper.insertActiveRankVOList(activeRankVOList);
        return result > 0;
    }
}
