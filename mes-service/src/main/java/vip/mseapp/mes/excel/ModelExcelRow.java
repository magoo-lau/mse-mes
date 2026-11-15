package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 制造建模导入/导出行（EasyExcel）：导入模板与导出文件同结构。
 *
 * <p>类型填中文（工厂/车间/产线/工位）；上级编码留空表示根节点；
 * 导入按类型排序（工厂→车间→产线→工位）处理后建上级，上级编码须为文件内或库内已存在的节点编码。
 */
@Data
public class ModelExcelRow {

    /** 工厂/车间/产线/工位 */
    @ExcelProperty(value = "类型*")
    @ColumnWidth(10)
    private String type;

    @ExcelProperty(value = "编码*")
    @ColumnWidth(18)
    private String code;

    @ExcelProperty(value = "名称*")
    @ColumnWidth(20)
    private String name;

    /** 上级节点编码（工厂/顶层留空） */
    @ExcelProperty(value = "上级编码")
    @ColumnWidth(18)
    private String parentCode;

    @ExcelProperty(value = "备注")
    @ColumnWidth(24)
    private String remark;
}
