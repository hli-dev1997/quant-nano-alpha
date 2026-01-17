package com.hao.datacollector.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hao.datacollector.common.utils.HttpUtil;
import com.hao.datacollector.dal.dao.BaseDataMapper;
import com.hao.datacollector.dal.dao.NewsMapper;
import com.hao.datacollector.dto.param.news.NewsQueryParam;
import com.hao.datacollector.dto.param.news.NewsRequestParams;
import com.hao.datacollector.properties.DataCollectorProperties;
import com.hao.datacollector.service.NewsService;
import com.hao.datacollector.web.vo.news.NewsInfoVO;
import com.hao.datacollector.web.vo.news.NewsQueryResultVO;
import constants.CommonConstants;
import constants.DataSourceConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import util.JsonUtil;
import util.PageUtil;
import exception.ExternalServiceException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 股票新闻采集与查询实现，负责从 Wind 新闻接口取数并维护本地库。
 * <p>
 * 具体流程：组装请求参数 → 调用统一的 {@link HttpUtil} 发送请求 →
 * 校验返回结构并解析 → 将新闻正文与股票关系分别写入数据库，同时提供分页查询能力。
 * </p>
 *
 * @author hli
 * @program: datacollector
 * @Date 2025-06-20 17:10:56
 * @description: 新闻相关实现类
 */
/**
 * 实现思路：
 * <p>
 * 1. 构造包含认证信息的 POST 请求拉取 Wind 股票新闻数据，并校验响应结构。
 * 2. 将返回的新闻内容解析成 VO，若无数据则记录异常股票；有数据则批量入库并维护关联关系。
 * 3. 对查询场景提供分页参数转换，最终通过 Mapper 返回符合条件的新闻列表。
 */
@Slf4j
@Service
public class NewsServiceImpl implements NewsService {

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private BaseDataMapper baseDataMapper;

    @Autowired
    private DataCollectorProperties properties;

    @Value("${wind_base.news.stock_news_url}")
    private String stockNewsUrl;

    /**
     * 转档股票新闻数据
     *
     * @param windCode 股票代码
     * @return 操作结果
     */
    @Override
    public Boolean transferNewsStockData(String windCode) {
        String url = DataSourceConstants.WIND_PROD_WGQ + stockNewsUrl;
        HashMap<String, String> header = new HashMap<>(4);
        header.put(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        NewsRequestParams params = new NewsRequestParams();
        params.setWindCode(windCode);
        // 发送请求，设置超时时间
        String bodyStr = HttpUtil.sendPostRequestTimeOut(url, JsonUtil.toJson(params), 10000, header);
        List<Object> jsonArray = JsonUtil.toList(bodyStr, Object.class);
        if (jsonArray == null || !CommonConstants.SUCCESS_CODE.equals(jsonArray.get(0))) {
            log.warn("日志记录|Log_message,NewsServiceImpl_transferNewsStockData_error=windCode={}", windCode);
            throw new ExternalServiceException("获取股票新闻数据失败|Get_stock_news_data_failed,windCode=" + windCode);
        }
        // Wind 返回的数组中，下标 3 为具体数据，先取出再解析
        Map<String, Object> dataMap = JsonUtil.toMap(JsonUtil.toJson(jsonArray.get(3)), String.class, Object.class);
        List<Map<String, Object>> newsListMap = JsonUtil.toType(JsonUtil.toJson(dataMap.get("value")), new TypeReference<List<Map<String, Object>>>() {});
        List<NewsInfoVO> newInfoVOList = JsonUtil.toList(JsonUtil.toJson(newsListMap.get(0).get("news")), NewsInfoVO.class);

        if (newInfoVOList.isEmpty()) {
            boolean insertAbnormalResult = baseDataMapper.insertAbnormalStock(windCode);
            log.warn("日志记录|Log_message,transferNewsStockData_error,windCode={},insertAbnormalResult={}", windCode, insertAbnormalResult);
            return false;
        }
        // 新闻正文与股票关系分两张表存储
        int newsInfoResultCount = newsMapper.insertNewsInfo(newInfoVOList);
        List<String> newsIdList = newInfoVOList.stream()
                .map(NewsInfoVO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        int relationResultCount = newsMapper.insertNewsStockRelation(newsIdList, params.getWindCode());
        log.info("日志记录|Log_message,NewsServiceImpl_transferNewsStockData_result=newsInfoResultCount={}_relationResultCount={}", newsInfoResultCount, relationResultCount);
        return newsInfoResultCount >= 0;
    }

    /**
     * 查询新闻基础数据
     *
     * @param queryParam 查询参数
     * @return 新闻数据列表
     */
    @Override
    public List<NewsQueryResultVO> queryNewsBaseData(NewsQueryParam queryParam) {
        log.info("日志记录|Log_message,NewsServiceImpl_queryNewsBaseData_start=queryParam={}", JsonUtil.toJson(queryParam));
        // 处理分页参数，将pageNo转换为offset
        if (queryParam.getPageNo() != null && queryParam.getPageSize() != null) {
            int offset = PageUtil.calculateOffset(queryParam.getPageNo(), queryParam.getPageSize());
            queryParam.setPageNo(offset);
        }
        // Mapper 根据分页条件查询基础新闻列表
        List<NewsQueryResultVO> result = newsMapper.queryNewsBaseData(queryParam);
        log.info("日志记录|Log_message,NewsServiceImpl_queryNewsBaseData_success=result_count={}", result.size());
        return result;
    }
}
