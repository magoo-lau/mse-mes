package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 检验方案导入/导出行（EasyExcel）：一行一个检验项目，同方案编码多行归组为一个方案整体保存。
 *
 * <p>检验类型填 IQC/IPQC/FQC。
 */
@Data
public class QcSchemeExcelRow {

    @ExcelProperty(value = "方案编码*")
    @ColumnWidth(18)
    private String schemeCode;

    @ExcelProperty(value = "方案名称*")
    @ColumnWidth(20)
    private String schemeName;

    /** IQC/IPQC/FQC */
    @ExcelProperty(value = "检验类型*")
    @ColumnWidth(10)
    private String qcType;

    /** 适用物料编码（可空=通用） */
    @ExcelProperty(value = "适用物料编码")
    @ColumnWidth(18)
    private String materialCode;

    @ExcelProperty(value = "抽样规则")
    @ColumnWidth(18)
    private String sampleRule;

    @ExcelProperty(value = "备注")
    @ColumnWidth(20)
    private String remark;

    @ExcelProperty(value = "检验项目名称*")
    @ColumnWidth(18)
    private String itemName;

    @ExcelProperty(value = "标准要求")
    @ColumnWidth(20)
    private String standard;

    @ExcelProperty(value = "检验方法")
    @ColumnWidth(16)
    private String method;
}
