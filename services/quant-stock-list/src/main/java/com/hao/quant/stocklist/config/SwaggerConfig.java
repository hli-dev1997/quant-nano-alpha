package com.hao.quant.stocklist.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 类说明 / Class Description:
 * 中文：Swagger/OpenAPI 接口文档配置类。
 * English: Swagger/OpenAPI API documentation configuration class.
 *
 * 设计目的 / Design Purpose:
 * 中文：配置 API 文档的基础信息，使接口文档更具可读性和专业性。
 * English: Configure API documentation metadata to make it more readable and professional.
 *
 * 核心实现思路 / Implementation:
 * 中文：通过 OpenAPI Bean 定义接口文档的标题、描述和版本信息。
 * English: Define API documentation title, description and version via OpenAPI Bean.
 */
@Configuration
public class SwaggerConfig {

    /**
     * 配置 OpenAPI 文档信息。
     *
     * 实现逻辑：
     * 1. 创建 OpenAPI 实例。
     * 2. 设置 API 标题、描述、版本等元数据。
     *
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("股票精选列表服务 API")
                        .description("提供精选股票列表查询能力（待其他模块产出数据后完善）")
                        .version("v1.0"));
    }
}
