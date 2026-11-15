package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 物料导入/导出行（EasyExcel）：导入模板与导出文件同结构。
 *
 * <p>类型填中文（原材料/半成品/成品，空默认原材料）；追溯粒度填中文（按批次/按序列号，空默认按批次）。
 */
@Data
public class MaterialExcelRow {

    @ExcelProperty(value = "物料编码*")
    @ColumnWidth(18)
    private String code;

    @ExcelProperty(value = "物料名称*")
    @ColumnWidth(20)
    private String name;

    @ExcelProperty(value = "规格型号")
    @ColumnWidth(20)
    private String spec;

    @ExcelProperty(value = "单位")
    @ColumnWidth(8)
    private String unit;

    /** 原材料/半成品/成品（空默认原材料） */
    @ExcelProperty(value = "类型")
    @ColumnWidth(10)
    private String type;

    /** 按批次/按序列号（空默认按批次） */
    @ExcelProperty(value = "追溯粒度")
    @ColumnWidth(10)
    private String traceMode;

    @ExcelProperty(value = "备注")
    @ColumnWidth(24)
    private String remark;
}
