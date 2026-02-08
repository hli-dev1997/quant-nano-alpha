package com.hao.datacollector.service;


import com.hao.datacollector.cache.DateCache;
import com.hao.datacollector.dto.param.abnormal.IndexSourceParam;
import constants.DateTimeFormatConstants;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import util.DateUtil;

import java.util.List;

/**
 * 异常指标服务测试
 *
 * 测试目的：
 * 1. 验证异常指标转档流程是否可正常执行。
 * 2. 验证主页与活跃榜相关接口的联动能力。
 *
 * 设计思路：
 * - 使用SpringBootTest加载上下文，直接调用服务方法验证链路。
 */
@Slf4j
@SpringBootTest
class AbnormalServiceImplTest {

    @Autowired
    private AbnormalService abnormalService;


    /**
     * 异常指标主页数据转档测试
     *
     * 实现逻辑：
     * 1. 遍历交易日并执行转档。
     * 2. 记录转档结果用于人工核验。
     */
    @Test
    public void getHomePage() {
        // 实现思路：循环交易日执行转档并记录结果
        //2021，2024已转，20250826(未完整)
        List<String> tradeDateList = DateUtil.formatLocalDateList(DateCache.Year2025TradeDateList, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
        for (String tradeDate : tradeDateList) {
            IndexSourceParam indexSourceParam = new IndexSourceParam();
            indexSourceParam.setTradeDate(tradeDate);
            Boolean result = false;
//            if (tradeDate.equals("20200111"。20220127,20230810)){
//                continue;
//            }
            try {
                result = abnormalService.transferHomePage(indexSourceParam);
            } catch (Exception e) {
                result = abnormalService.transferHomePage(indexSourceParam);
            }
            log.info("主页转档结果|Home_page_transfer_result,tradeDate={},result={}", tradeDate, result);
        }


//        Boolean sourceListOfSeats = abnormalService.transferListOfSeats(3, 0, 1, 10000, 1, -1);
//
//        Boolean result = abnormalService.transferActiveRank(3, 0, 10000, 1, -1);
    }
}
