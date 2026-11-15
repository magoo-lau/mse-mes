package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM 导入/导出行（EasyExcel）：一行一个子件行，同 BOM 编码多行归组为一个 BOM 整体保存。
 */
@Data
public class BomExcelRow {

    @ExcelProperty(value = "BOM编码*")
    @ColumnWidth(18)
    private String bomCode;

    /** 父项物料编码 */
    @ExcelProperty(value = "父项物料编码*")
    @ColumnWidth(18)
    private String parentMaterialCode;

    /** 如 V1.0（空默认 V1.0） */
    @ExcelProperty(value = "版本")
    @ColumnWidth(10)
    private String version;

    @ExcelProperty(value = "备注")
    @ColumnWidth(20)
    private String remark;

    @ExcelProperty(value = "子件物料编码*")
    @ColumnWidth(18)
    private String childMaterialCode;

    @ExcelProperty(value = "用量*")
    @ColumnWidth(10)
    private BigDecimal qty;

    /** 损耗率百分比（可空） */
    @ExcelProperty(value = "损耗率(%)")
    @ColumnWidth(12)
    private BigDecimal lossRate;
}
