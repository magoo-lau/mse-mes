package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 工艺路线导入/导出行（EasyExcel）：一行一个工序，同路线编码多行归组为一条路线整体保存。
 *
 * <p>工序号可空（按文件行序）；产线填产线编码；是否质检点填 是/否。
 */
@Data
public class RouteExcelRow {

    @ExcelProperty(value = "路线编码*")
    @ColumnWidth(18)
    private String routeCode;

    @ExcelProperty(value = "路线名称*")
    @ColumnWidth(20)
    private String routeName;

    /** 如 V1.0（空默认 V1.0） */
    @ExcelProperty(value = "版本")
    @ColumnWidth(10)
    private String version;

    /** 适用物料编码（可空=通用） */
    @ExcelProperty(value = "适用物料编码")
    @ColumnWidth(18)
    private String materialCode;

    @ExcelProperty(value = "备注")
    @ColumnWidth(20)
    private String remark;

    /** 可空，空按文件行序 */
    @ExcelProperty(value = "工序号")
    @ColumnWidth(8)
    private Integer stepNo;

    @ExcelProperty(value = "工序名称*")
    @ColumnWidth(16)
    private String stepName;

    /** 默认产线编码（可空） */
    @ExcelProperty(value = "产线编码")
    @ColumnWidth(18)
    private String lineCode;

    /** 秒/件 */
    @ExcelProperty(value = "标准工时(秒)")
    @ColumnWidth(12)
    private Integer standardTime;

    /** 是/否（空默认否） */
    @ExcelProperty(value = "是否质检点")
    @ColumnWidth(10)
    private String qcRequired;
}
