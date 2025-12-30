package com.hao.datacollector.web.controller;

import com.hao.datacollector.dto.param.base.CloudDataParams;
import com.hao.datacollector.dto.param.base.StockInfoDailyDTO;
import constants.DateTimeFormatConstants;
import util.DateUtil;
import util.PageUtil;
import com.hao.datacollector.dto.param.stock.StockBasicInfoQueryParam;
import com.hao.datacollector.dto.param.stock.StockMarketDataQueryParam;
import com.hao.datacollector.service.BaseDataService;
import com.hao.datacollector.web.vo.stock.StockBasicInfoQueryResultVO;
import com.hao.datacollector.web.vo.stock.StockMarketDataQueryResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author hli
 * @program: datacollector
 * @Date 2025-06-14 15:55:50
 * @description: 基础数据处理controller
 */
@Tag(name = "基础数据", description = "基础数据处理接口")
@Slf4j
@RestController
@RequestMapping("/base_date")
public class BaseDataController {
    @Autowired
    private BaseDataService baseDataService;

    @Operation(
            summary = "转档交易日历",
            description = "根据时间区间转档交易日历数据(注：会清空原有表数据)"
    )
    @PostMapping("/set_trade_date")
    public Boolean setTradeDateList(
            @Parameter(description = "起始日", example = "日历起始日:例如20250614")
            @RequestParam String startTime,
            @Parameter(description = "终止日", example = "60")
            @RequestParam(defaultValue = "20250614") String endTime) {
        log.info("设置交易日历|Set_trade_date,startTime={},endTime={}", startTime, endTime);
        return baseDataService.setTradeDateList(startTime, endTime);
    }

    @Operation(
            summary = "获取交易日历",
            description = "根据时间区间获取交易日历"
    )
    @GetMapping("/get_trade_date")
    public List<String> getTradeDateListByTime(
            @Parameter(description = "起始日", example = "日历起始日:例如20250614")
            @RequestParam String startTime,
            @Parameter(description = "终止日", example = "60")
            @RequestParam(defaultValue = "20250614") String endTime) {
        log.info("获取交易日历|Get_trade_date_list,startTime={},endTime={}", startTime, endTime);
        List<LocalDate> dateListByTime = baseDataService.getTradeDateListByTime(startTime, endTime);
        //默认转换为8位数字日期格式（如：20190214）
        return DateUtil.formatLocalDateList(dateListByTime, DateTimeFormatConstants.EIGHT_DIGIT_DATE_FORMAT);
    }

    @Operation(
            summary = "查询股票基本信息",
            description = "根据查询条件获取股票基本信息列表"
    )
    @GetMapping("/stock_basic_list")
    public List<StockBasicInfoQueryResultVO> queryStockBasicInfo(
            @Parameter(description = "股票代码", example = "000001.SZ")
            @RequestParam(required = false) String windCode,
            @Parameter(description = "股票名称", example = "平安银行")
            @RequestParam(required = false) String windName,
            @Parameter(description = "申万行业代码", example = "801780")
            @RequestParam(required = false) String swIndustryCode,
            @Parameter(description = "申万行业名称", example = "银行")
            @RequestParam(required = false) String swIndustryName,
            @Parameter(description = "中信行业代码", example = "CI005001")
            @RequestParam(required = false) String citicIndustryCode,
            @Parameter(description = "中信行业名称", example = "银行")
            @RequestParam(required = false) String citicIndustryName,
            @Parameter(description = "上市日期开始", example = "2020-01-01")
            @RequestParam(required = false) String listingDateStart,
            @Parameter(description = "上市日期结束", example = "2023-12-31")
            @RequestParam(required = false) String listingDateEnd,
            @Parameter(description = "股票状态", example = "L")
            @RequestParam(required = false) String statusExistence,
            @Parameter(description = "概念板块", example = "新能源")
            @RequestParam(required = false) String conceptPlates,
            @Parameter(description = "热门概念", example = "人工智能")
            @RequestParam(required = false) String hotConcepts,
            @Parameter(description = "产业链", example = "新能源汽车")
            @RequestParam(required = false) String industryChain,
            @Parameter(description = "是否长期跌破净资产", example = "N")
            @RequestParam(required = false) String isLongBelowNetAsset,
            @Parameter(description = "公司简介关键词")
            @RequestParam(required = false) String companyProfile,
            @Parameter(description = "经营范围关键词")
            @RequestParam(required = false) String businessScope,
            @Parameter(description = "总股本最小值")
            @RequestParam(required = false) Long totalSharesMin,
            @Parameter(description = "总股本最大值")
            @RequestParam(required = false) Long totalSharesMax,
            @Parameter(description = "流通股本最小值")
            @RequestParam(required = false) Long floatSharesMin,
            @Parameter(description = "流通股本最大值")
            @RequestParam(required = false) Long floatSharesMax,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNo,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize) {
        StockBasicInfoQueryParam queryParam = new StockBasicInfoQueryParam();
        queryParam.setWindCode(windCode);
        queryParam.setWindName(windName);
        queryParam.setSwIndustryCode(swIndustryCode);
        queryParam.setSwIndustryName(swIndustryName);
        queryParam.setCiticIndustryCode(citicIndustryCode);
        queryParam.setCiticIndustryName(citicIndustryName);
        queryParam.setListingDateStart(listingDateStart != null ? LocalDate.parse(listingDateStart) : null);
        queryParam.setListingDateEnd(listingDateEnd != null ? LocalDate.parse(listingDateEnd) : null);
        queryParam.setStatusExistence(statusExistence);
        queryParam.setConceptPlates(conceptPlates);
        queryParam.setHotConcepts(hotConcepts);
        queryParam.setIndustryChain(industryChain);
        queryParam.setIsLongBelowNetAsset(isLongBelowNetAsset);
        queryParam.setCompanyProfile(companyProfile);
        queryParam.setBusinessScope(businessScope);
        queryParam.setTotalSharesMin(totalSharesMin);
        queryParam.setTotalSharesMax(totalSharesMax);
        queryParam.setFloatSharesMin(floatSharesMin);
        queryParam.setFloatSharesMax(floatSharesMax);
        queryParam.setPageNo(pageNo);
        queryParam.setPageSize(pageSize);
        log.info("查询股票基础信息|Query_stock_basic_info,queryParam={}", queryParam);
        return baseDataService.queryStockBasicInfo(queryParam);
    }

    @Operation(
            summary = "查询股票行情数据",
            description = "根据查询条件获取股票行情数据列表"
    )
    @GetMapping("/stock_market_list")
    public List<StockMarketDataQueryResultVO> queryStockMarketData(
            @Parameter(description = "股票代码", example = "000001.SZ")
            @RequestParam(required = false) String windCode,
            @Parameter(description = "交易日期开始", example = "2023-01-01")
            @RequestParam(required = false) String tradeDateStart,
            @Parameter(description = "交易日期结束", example = "2023-12-31")
            @RequestParam(required = false) String tradeDateEnd,
            @Parameter(description = "股票名称", example = "平安银行")
            @RequestParam(required = false) String windName,
            @Parameter(description = "最新概念", example = "金融科技")
            @RequestParam(required = false) String latestconcept,
            @Parameter(description = "所属产业链板块", example = "金融服务")
            @RequestParam(required = false) String chain,
            @Parameter(description = "开盘价最小值", example = "10.0")
            @RequestParam(required = false) String openMin,
            @Parameter(description = "开盘价最大值", example = "50.0")
            @RequestParam(required = false) String openMax,
            @Parameter(description = "最高价最小值", example = "10.0")
            @RequestParam(required = false) String highMin,
            @Parameter(description = "最高价最大值", example = "50.0")
            @RequestParam(required = false) String highMax,
            @Parameter(description = "最低价最小值", example = "10.0")
            @RequestParam(required = false) String lowMin,
            @Parameter(description = "最低价最大值", example = "50.0")
            @RequestParam(required = false) String lowMax,
            @Parameter(description = "收盘价最小值", example = "10.0")
            @RequestParam(required = false) String closeMin,
            @Parameter(description = "收盘价最大值", example = "50.0")
            @RequestParam(required = false) String closeMax,
            @Parameter(description = "成交量加权平均价最小值", example = "10.0")
            @RequestParam(required = false) String vwapMin,
            @Parameter(description = "成交量加权平均价最大值", example = "50.0")
            @RequestParam(required = false) String vwapMax,
            @Parameter(description = "成交量最小值", example = "1000000")
            @RequestParam(required = false) Long volumeBtinMin,
            @Parameter(description = "成交量最大值", example = "100000000")
            @RequestParam(required = false) Long volumeBtinMax,
            @Parameter(description = "成交额最小值", example = "10000000")
            @RequestParam(required = false) String amountBtinMin,
            @Parameter(description = "成交额最大值", example = "1000000000")
            @RequestParam(required = false) String amountBtinMax,
            @Parameter(description = "涨跌幅最小值", example = "-10.0")
            @RequestParam(required = false) String pctChgMin,
            @Parameter(description = "涨跌幅最大值", example = "10.0")
            @RequestParam(required = false) String pctChgMax,
            @Parameter(description = "换手率最小值", example = "0.5")
            @RequestParam(required = false) String turnMin,
            @Parameter(description = "换手率最大值", example = "20.0")
            @RequestParam(required = false) String turnMax,
            @Parameter(description = "自由换手率最小值", example = "0.5")
            @RequestParam(required = false) String freeTurnMin,
            @Parameter(description = "自由换手率最大值", example = "20.0")
            @RequestParam(required = false) String freeTurnMax,
            @Parameter(description = "振幅最小值", example = "1.0")
            @RequestParam(required = false) String maxupMin,
            @Parameter(description = "振幅最大值", example = "15.0")
            @RequestParam(required = false) String maxupMax,
            @Parameter(description = "跌幅最小值", example = "1.0")
            @RequestParam(required = false) String maxdownMin,
            @Parameter(description = "跌幅最大值", example = "15.0")
            @RequestParam(required = false) String maxdownMax,
            @Parameter(description = "交易状态", example = "交易")
            @RequestParam(required = false) String tradeStatus,
            @Parameter(description = "总市值最小值", example = "1000000000")
            @RequestParam(required = false) String evMin,
            @Parameter(description = "总市值最大值", example = "100000000000")
            @RequestParam(required = false) String evMax,
            @Parameter(description = "流通市值最小值", example = "1000000000")
            @RequestParam(required = false) String mktFreesharesMin,
            @Parameter(description = "流通市值最大值", example = "100000000000")
            @RequestParam(required = false) String mktFreesharesMax,
            @Parameter(description = "开盘集合竞价价格最小值", example = "10.0")
            @RequestParam(required = false) String openAuctionPriceMin,
            @Parameter(description = "开盘集合竞价价格最大值", example = "50.0")
            @RequestParam(required = false) String openAuctionPriceMax,
            @Parameter(description = "开盘集合竞价成交量最小值", example = "1000000")
            @RequestParam(required = false) Long openAuctionVolumeMin,
            @Parameter(description = "开盘集合竞价成交量最大值", example = "100000000")
            @RequestParam(required = false) Long openAuctionVolumeMax,
            @Parameter(description = "开盘集合竞价成交额最小值", example = "10000000")
            @RequestParam(required = false) String openAuctionAmountMin,
            @Parameter(description = "开盘集合竞价成交额最大值", example = "1000000000")
            @RequestParam(required = false) String openAuctionAmountMax,
            @Parameter(description = "主力资金买入金额最小值", example = "1000000")
            @RequestParam(required = false) String mfdBuyamtAtMin,
            @Parameter(description = "主力资金买入金额最大值", example = "100000000")
            @RequestParam(required = false) String mfdBuyamtAtMax,
            @Parameter(description = "主力资金卖出金额最小值", example = "1000000")
            @RequestParam(required = false) String mfdSellamtAtMin,
            @Parameter(description = "主力资金卖出金额最大值", example = "100000000")
            @RequestParam(required = false) String mfdSellamtAtMax,
            @Parameter(description = "主力资金买入量最小值", example = "1000000")
            @RequestParam(required = false) Long mfdBuyvolAtMin,
            @Parameter(description = "主力资金买入量最大值", example = "100000000")
            @RequestParam(required = false) Long mfdBuyvolAtMax,
            @Parameter(description = "主力资金卖出量最小值", example = "1000000")
            @RequestParam(required = false) Long mfdSellvolAtMin,
            @Parameter(description = "主力资金卖出量最大值", example = "100000000")
            @RequestParam(required = false) Long mfdSellvolAtMax,
            @Parameter(description = "主力资金净流入最小值", example = "1000000")
            @RequestParam(required = false) String mfdInflowMMin,
            @Parameter(description = "主力资金净流入最大值", example = "100000000")
            @RequestParam(required = false) String mfdInflowMMax,
            @Parameter(description = "主力资金净流入占比最小值", example = "0.1")
            @RequestParam(required = false) String mfdInflowproportionMMin,
            @Parameter(description = "主力资金净流入占比最大值", example = "10.0")
            @RequestParam(required = false) String mfdInflowproportionMMax,
            @Parameter(description = "技术指标换手率5日最小值", example = "0.5")
            @RequestParam(required = false) String techTurnoverrate5Min,
            @Parameter(description = "技术指标换手率5日最大值", example = "20.0")
            @RequestParam(required = false) String techTurnoverrate5Max,
            @Parameter(description = "技术指标换手率10日最小值", example = "0.5")
            @RequestParam(required = false) String techTurnoverrate10Min,
            @Parameter(description = "技术指标换手率10日最大值", example = "20.0")
            @RequestParam(required = false) String techTurnoverrate10Max,
            @Parameter(description = "ESG评级", example = "A")
            @RequestParam(required = false) String esgRatingWind,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNo,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize) {
        StockMarketDataQueryParam queryParam = new StockMarketDataQueryParam();
        queryParam.setWindCode(windCode);
        queryParam.setTradeDateStart(tradeDateStart != null ? LocalDate.parse(tradeDateStart) : null);
        queryParam.setTradeDateEnd(tradeDateEnd != null ? LocalDate.parse(tradeDateEnd) : null);
        queryParam.setWindName(windName);
        queryParam.setLatestconcept(latestconcept);
        queryParam.setChain(chain);
        queryParam.setOpenMin(openMin != null ? new BigDecimal(openMin) : null);
        queryParam.setOpenMax(openMax != null ? new BigDecimal(openMax) : null);
        queryParam.setHighMin(highMin != null ? new BigDecimal(highMin) : null);
        queryParam.setHighMax(highMax != null ? new BigDecimal(highMax) : null);
        queryParam.setLowMin(lowMin != null ? new BigDecimal(lowMin) : null);
        queryParam.setLowMax(lowMax != null ? new BigDecimal(lowMax) : null);
        queryParam.setCloseMin(closeMin != null ? new BigDecimal(closeMin) : null);
        queryParam.setCloseMax(closeMax != null ? new BigDecimal(closeMax) : null);
        queryParam.setVwapMin(vwapMin != null ? new BigDecimal(vwapMin) : null);
        queryParam.setVwapMax(vwapMax != null ? new BigDecimal(vwapMax) : null);
        queryParam.setVolumeBtinMin(volumeBtinMin);
        queryParam.setVolumeBtinMax(volumeBtinMax);
        queryParam.setAmountBtinMin(amountBtinMin != null ? new BigDecimal(amountBtinMin) : null);
        queryParam.setAmountBtinMax(amountBtinMax != null ? new BigDecimal(amountBtinMax) : null);
        queryParam.setPctChgMin(pctChgMin != null ? new BigDecimal(pctChgMin) : null);
        queryParam.setPctChgMax(pctChgMax != null ? new BigDecimal(pctChgMax) : null);
        queryParam.setTurnMin(turnMin != null ? new BigDecimal(turnMin) : null);
        queryParam.setTurnMax(turnMax != null ? new BigDecimal(turnMax) : null);
        queryParam.setFreeTurnMin(freeTurnMin != null ? new BigDecimal(freeTurnMin) : null);
        queryParam.setFreeTurnMax(freeTurnMax != null ? new BigDecimal(freeTurnMax) : null);
        queryParam.setMaxupMin(maxupMin != null ? new BigDecimal(maxupMin) : null);
        queryParam.setMaxupMax(maxupMax != null ? new BigDecimal(maxupMax) : null);
        queryParam.setMaxdownMin(maxdownMin != null ? new BigDecimal(maxdownMin) : null);
        queryParam.setMaxdownMax(maxdownMax != null ? new BigDecimal(maxdownMax) : null);
        queryParam.setTradeStatus(tradeStatus);
        queryParam.setEvMin(evMin != null ? new BigDecimal(evMin) : null);
        queryParam.setEvMax(evMax != null ? new BigDecimal(evMax) : null);
        queryParam.setMktFreesharesMin(mktFreesharesMin != null ? new BigDecimal(mktFreesharesMin) : null);
        queryParam.setMktFreesharesMax(mktFreesharesMax != null ? new BigDecimal(mktFreesharesMax) : null);
        queryParam.setOpenAuctionPriceMin(openAuctionPriceMin != null ? new BigDecimal(openAuctionPriceMin) : null);
        queryParam.setOpenAuctionPriceMax(openAuctionPriceMax != null ? new BigDecimal(openAuctionPriceMax) : null);
        queryParam.setOpenAuctionVolumeMin(openAuctionVolumeMin);
        queryParam.setOpenAuctionVolumeMax(openAuctionVolumeMax);
        queryParam.setOpenAuctionAmountMin(openAuctionAmountMin != null ? new BigDecimal(openAuctionAmountMin) : null);
        queryParam.setOpenAuctionAmountMax(openAuctionAmountMax != null ? new BigDecimal(openAuctionAmountMax) : null);
        queryParam.setMfdBuyamtAtMin(mfdBuyamtAtMin != null ? new BigDecimal(mfdBuyamtAtMin) : null);
        queryParam.setMfdBuyamtAtMax(mfdBuyamtAtMax != null ? new BigDecimal(mfdBuyamtAtMax) : null);
        queryParam.setMfdSellamtAtMin(mfdSellamtAtMin != null ? new BigDecimal(mfdSellamtAtMin) : null);
        queryParam.setMfdSellamtAtMax(mfdSellamtAtMax != null ? new BigDecimal(mfdSellamtAtMax) : null);
        queryParam.setMfdBuyvolAtMin(mfdBuyvolAtMin);
        queryParam.setMfdBuyvolAtMax(mfdBuyvolAtMax);
        queryParam.setMfdSellvolAtMin(mfdSellvolAtMin);
        queryParam.setMfdSellvolAtMax(mfdSellvolAtMax);
        queryParam.setMfdInflowMMin(mfdInflowMMin != null ? new BigDecimal(mfdInflowMMin) : null);
        queryParam.setMfdInflowMMax(mfdInflowMMax != null ? new BigDecimal(mfdInflowMMax) : null);
        queryParam.setMfdInflowproportionMMin(mfdInflowproportionMMin != null ? new BigDecimal(mfdInflowproportionMMin) : null);
        queryParam.setMfdInflowproportionMMax(mfdInflowproportionMMax != null ? new BigDecimal(mfdInflowproportionMMax) : null);
        queryParam.setTechTurnoverrate5Min(techTurnoverrate5Min != null ? new BigDecimal(techTurnoverrate5Min) : null);
        queryParam.setTechTurnoverrate5Max(techTurnoverrate5Max != null ? new BigDecimal(techTurnoverrate5Max) : null);
        queryParam.setTechTurnoverrate10Min(techTurnoverrate10Min != null ? new BigDecimal(techTurnoverrate10Min) : null);
        queryParam.setTechTurnoverrate10Max(techTurnoverrate10Max != null ? new BigDecimal(techTurnoverrate10Max) : null);
        queryParam.setEsgRatingWind(esgRatingWind);
        queryParam.setPageNo(pageNo);
        queryParam.setPageSize(pageSize);
        log.info("查询股票行情数据|Query_stock_market_data,queryParam={}", queryParam);
        return baseDataService.queryStockMarketData(queryParam);
    }

    @Operation(
            summary = "获取云数据",
            description = "获取云数据"
    )
    @PostMapping("/get_cloud_data")
    public List<List<Object>> getCloudData(
            @Parameter(description = "云数据请求参数", required = true)
            @RequestBody CloudDataParams params) {
        log.info("获取云数据|Get_cloud_data,params={}", params);
        return baseDataService.getCloudData(params);
    }

    @Operation(
            summary = "批量插入日频股票信息",
            description = "将传入的股票列表批量插入或更新到日频股票信息表中"
    )
    @PostMapping("/insert_stock_info_daily")
    public Boolean batchInsertStockInfoDaily(
            @Parameter(description = "股票信息列表", required = true)
            @RequestBody List<StockInfoDailyDTO> stockList,
            @Parameter(description = "交易日", example = "2025-06-21", required = true)
            @RequestParam String tradeDate) {
        log.info("批量插入日频股票信息|Batch_insert_stock_info_daily,count={},tradeDate={}", stockList.size(), tradeDate);
        return baseDataService.batchInsertStockInfoDaily(stockList, tradeDate);
    }

    @Operation(
            summary = "查询日频股票信息",
            description = "根据交易日查询当天的所有A股股票列表"
    )
    @GetMapping("/stock_info_daily_list")
    public List<StockInfoDailyDTO> queryStockInfoDaily(
            @Parameter(description = "交易日，格式 yyyy-MM-dd，不传则默认为当天", example = "2025-06-21")
            @RequestParam(required = false) String tradeDate,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNo,
            @Parameter(description = "每页大小", example = "1000")
            @RequestParam(defaultValue = "1000") Integer pageSize) {
        String queryDate = tradeDate;
        if (!StringUtils.hasText(queryDate)) {
            queryDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        // 计算偏移量
        int offset = PageUtil.calculateOffset(pageNo, pageSize);
        log.info("查询日频股票信息|Query_stock_info_daily,tradeDate={},pageNo={},pageSize={}", queryDate, pageNo, pageSize);
        return baseDataService.queryStockInfoDaily(queryDate, offset, pageSize);
    }
}
