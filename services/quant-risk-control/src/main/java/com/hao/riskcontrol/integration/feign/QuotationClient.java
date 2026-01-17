package com.hao.riskcontrol.integration.feign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * @author hli
 * @program: quant-nano-alpha
 * @Date 2025-08-25 19:39:33
 * @description: 外部行情服务客户端
 */
@FeignClient(name = "quotation", url = "http://114.80.154.45")
// TODO: 硬编码IP地址，建议迁移到配置文件 (Hardcoded IP, consider moving to configuration)
public interface QuotationClient {

    Integer MAX_WINDCODE_SIZE = 100;

    String FIX_URL = "/wstock_quotation";

    @Operation(summary = "批量获取实时行情数据(get+post)")
    @Parameters({
            @Parameter(name = "wind.sessionid", description = "sessionId", in = ParameterIn.HEADER),
            @Parameter(name = "version", description = "接口版本，默认为1.0, 推荐使用2.0, 性能更好", required = true, in = ParameterIn.HEADER),
            @Parameter(name = "windCodes", description = "windCodes", required = true, in = ParameterIn.QUERY),
            @Parameter(name = "indicatorIds", description = "指标ID", required = true, in = ParameterIn.QUERY)
    })
    @GetMapping(value = FIX_URL + "/quotation" + "/batch_real_time")
    Map<String, Map<Integer, Object>> batchRealTimeQuotation(
            @RequestHeader(value = "wind.sessionid") String session,
            @RequestHeader(required = false, defaultValue = "2.0") String version,
            @RequestParam List<String> windCodes,
            @RequestParam List<Integer> indicatorIds);

}
