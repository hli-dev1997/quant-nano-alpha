package com.hao.datacollector.common.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import exception.DataException;

/**
 * Excel读取工具类
 *
 * 职责：将Excel表格读取为行数据或表头列表。
 *
 * 设计目的：
 * 1. 统一Excel读取逻辑，避免重复处理单元格类型。
 * 2. 提供稳定的数据结构供转换层复用。
 *
 * 为什么需要该类：
 * - Excel字段解析细节繁多，需要集中维护。
 *
 * 核心实现思路：
 * - 使用POI读取首个Sheet并按行列构建Map列表。
 */
public class ExcelReaderUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ExcelReaderUtil.class);
    private static List<String> headers = new ArrayList<>();

    /**
     * 读取Excel内容为行数据列表
     *
     * 实现逻辑：
     * 1. 读取首个Sheet的表头。
     * 2. 从第二行开始读取数据并按表头映射成Map。
     *
     * @param file Excel文件
     * @return 行数据列表
     */
    public static List<Map<String, String>> readExcel(File file) {
        // 实现思路：表头作为键，逐行读取构造Map
        try (InputStream inputStream = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0); // 读取第一个Sheet
            List<Map<String, String>> result = new ArrayList<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return result;

            int columnCount = headerRow.getPhysicalNumberOfCells();
            List<String> headers = new ArrayList<>();

            for (int i = 0; i < columnCount; i++) {
                headers.add(getCellValue(headerRow.getCell(i)));
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();
                for (int j = 0; j < columnCount; j++) {
                    String header = headers.get(j);
                    String cellValue = getCellValue(row.getCell(j));
                    rowData.put(header, cellValue);
                }
                result.add(rowData);
            }

            return result;
        } catch (Exception e) {
            throw new DataException("读取Excel文件失败|Read_Excel_file_failed: " + e.getMessage(), e);
        }
    }

    /**
     * 读取单元格值
     *
     * 实现逻辑：
     * 1. 根据单元格类型选择读取方式。
     * 2. 统一转换为字符串。
     *
     * @param cell 单元格
     * @return 单元格值
     */
    private static String getCellValue(Cell cell) {
        // 实现思路：统一处理字符串、数值、布尔与公式类型
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell) ?
                    cell.getDateCellValue().toString() :
                    String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    /**
     * 只读取Excel第一行表头，返回表头列名列表
     *
     * 实现逻辑：
     * 1. 读取首个Sheet的第一行。
     * 2. 遍历列并返回表头列表。
     *
     * @param file Excel文件
     * @return 表头列表
     */
    public static List<String> readHeaders(File file) {
        // 实现思路：只读取表头行避免全量加载
        try (InputStream inputStream = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return Collections.emptyList();

            int columnCount = headerRow.getPhysicalNumberOfCells();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                headers.add(getCellValue(headerRow.getCell(i)));
            }
            return headers;
        } catch (Exception e) {
            throw new DataException("读取Excel表头失败|Read_Excel_header_failed: " + e.getMessage(), e);
        }
    }

}
