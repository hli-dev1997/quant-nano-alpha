package com.hao.datacollector.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.hao.datacollector.common.utils.HttpUtil;
import com.hao.datacollector.dal.dao.AnnouncementMapper;
import com.hao.datacollector.properties.DataCollectorProperties;
import com.hao.datacollector.service.AnnouncementService;
import com.hao.datacollector.web.vo.announcement.AnnouncementVO;
import com.hao.datacollector.web.vo.announcement.BigEventVO;
import constants.DataSourceConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.stream.Collectors;
import exception.ExternalServiceException;

/**
 * 公告与大事数据采集实现，负责基于 Wind API 拉取并落库对应信息。
 * <p>
 * 统一流程：组装查询参数 → 通过 {@link HttpUtil} 请求 → 校验状态 →
 * 将 JSON 解析为 VO 并在必要时格式化日期后写入数据库。
 * </p>
 *
 * @author hli
 * @Date 2025-06-24 13:52:41
 * @description: 公告数据实现类
 */
/**
 * 实现思路：
 * <p>
 * 1. 基于配置的 Wind 接口构造 GET 请求并附带分页、时间等查询条件。
 * 2. 将返回的公告/大事件 JSON 数据转换为 VO 列表，并在入库前做格式标准化。
 * 3. 通过 Mapper 完成批量插入，实现公告与大事件的数据落地与查询能力。
 */
@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Autowired
    private DataCollectorProperties properties;

    @Value("${wind_base.announcement.url}")
    private String AnnouncementUrl;

    @Value("${wind_base.event.url}")
    private String eventUrl;


    private static final String DATE_TIME_SEPARATOR = "T";
    private static final String UTC_FLAG = "Z";

    private static final String SUCCESS_FLAG = "200 OK";

    /**
     * 个股公告数据源
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param pageNo    页号
     * @param pageSize  页面规模
     * @return 个股公告数据
     */
    @Override
    public List<AnnouncementVO> getAnnouncementSourceData(String windCode, String startDate, String endDate, Integer pageNo, Integer pageSize) {
        String url = String.format(DataSourceConstants.WIND_PROD_WGQ + AnnouncementUrl, windCode);
        org.springframework.http.HttpHeaders headers = new HttpHeaders();
        headers.set(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        // 查询参数透传给 Wind 接口，保持分页能力
        queryParams.add("startDate", startDate);
        queryParams.add("endDate", endDate);
        queryParams.add("pageNo", String.valueOf(pageNo));
        queryParams.add("pageSize", String.valueOf(pageSize));
        ResponseEntity<String> response = HttpUtil.sendGetWithParams(url, queryParams, headers, 100000, 100000);
        if (!SUCCESS_FLAG.equals(response.getStatusCode().toString())) {
            throw new ExternalServiceException("获取公告或大事数据失败|Get_announcement_data_failed,statusCode=" + response.getStatusCode());
        }
        // 成功返回后直接反序列化为公告列表
        List<AnnouncementVO> announcementList = JSON.parseObject(response.getBody(), new TypeReference<List<AnnouncementVO>>() {
        });
        return announcementList;
    }

    /**
     * 转档公告数据源
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param pageNo    页号
     * @param pageSize  页面规模
     * @return 操作结果
     */
    @Override
    public Boolean transferAnnouncement(String windCode, String startDate, String endDate, Integer pageNo, Integer pageSize) {
        List<AnnouncementVO> announcementVOList = getAnnouncementSourceData(windCode, startDate, endDate, pageNo, pageSize);
        if (announcementVOList.isEmpty()) {
            log.warn("日志记录|Log_message,transferAnnouncement_announcementVOList.isEmpty()!windCode={}", windCode);
            return false;
        }
        // 批量转换日期格式
        announcementVOList.stream()
                .peek(announcement -> announcement.setDate(getFormattedDate(announcement.getDate())))
                .collect(Collectors.toList());
        // Mapper 负责批量 upsert，入库后返回成功标记
        int insertResult = announcementMapper.insertAnnouncementSourceData(announcementVOList, windCode);
        return insertResult > 0;
    }

    /**
     * 转换可插入数据库Date
     *
     * @param date 待转换字符串:2025-06-20T00:00:00Z
     * @return 转换后的代码
     */
    public String getFormattedDate(String date) {
        if (date != null && date.contains(DATE_TIME_SEPARATOR)) {
            return date.replace(DATE_TIME_SEPARATOR, " ").replace(UTC_FLAG, "");
        }
        return date;
    }

    /**
     * 个股大事数据源
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param pageNo    页号
     * @param pageSize  页面规模
     * @return 个股大事数据源
     */
    @Override
    public List<BigEventVO> getEventSourceData(String windCode, String startDate, String endDate, Integer pageNo, Integer pageSize) {
        String url = String.format(DataSourceConstants.WIND_PROD_WGQ + eventUrl, windCode);
        org.springframework.http.HttpHeaders headers = new HttpHeaders();
        headers.set(DataSourceConstants.WIND_POINT_SESSION_NAME, properties.getWindSessionId());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("startDate", startDate);
        queryParams.add("endDate", endDate);
        queryParams.add("pageNo", String.valueOf(pageNo));
        queryParams.add("pageSize", String.valueOf(pageSize));
        ResponseEntity<String> response = HttpUtil.sendGetWithParams(url, queryParams, headers, 10000, 10000);
        if (!SUCCESS_FLAG.equals(response.getStatusCode().toString())) {
            throw new ExternalServiceException("获取公告或大事数据失败|Get_announcement_data_failed,statusCode=" + response.getStatusCode());
        }
        // 直接将 JSON 数组映射为大事 VO 列表
        List<BigEventVO> eventVOList = JSON.parseObject(response.getBody(), new TypeReference<List<BigEventVO>>() {
        });
        return eventVOList;
    }

    /**
     * 转档大事数据源
     *
     * @param windCode  股票代码
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @param pageNo    页号
     * @param pageSize  页面规模
     * @return 操作结果
     */
    @Override
    public Boolean transferEvent(String windCode, String startDate, String endDate, Integer pageNo, Integer pageSize) {
        List<BigEventVO> eventSourceList = getEventSourceData(windCode, startDate, endDate, pageNo, pageSize);
        if (eventSourceList == null || eventSourceList.isEmpty()) {
            log.warn("日志记录|Log_message,transferEvent_eventSourceList.isEmpty()!windCode={}", windCode);
            return false;
        }
        // 调用 Mapper 批量写入事件数据
        int insertResult = announcementMapper.insertEventSource(eventSourceList, windCode);
        return insertResult > 0;
    }
}
