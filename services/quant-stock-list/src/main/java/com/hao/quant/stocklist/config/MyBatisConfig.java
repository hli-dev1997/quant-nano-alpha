package com.hao.quant.stocklist.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 类说明 / Class Description:
 * 中文：MyBatis 数据访问层配置类。
 * English: MyBatis data access layer configuration class.
 *
 * 设计目的 / Design Purpose:
 * 中文：配置 MyBatis Mapper 扫描路径，统一管理数据访问层组件。
 * English: Configure MyBatis Mapper scan path to centrally manage data access layer components.
 *
 * 核心实现思路 / Implementation:
 * 中文：通过 @MapperScan 指定 DAO 接口所在包路径，Spring 自动为接口生成代理实现。
 * English: Use @MapperScan to specify DAO interface package path; Spring auto-generates proxy implementations.
 */
@Configuration
@MapperScan("com.hao.quant.stocklist.dal.dao")
public class MyBatisConfig {
    // TODO: 待其他模块产出数据后添加 Mapper 配置
}
