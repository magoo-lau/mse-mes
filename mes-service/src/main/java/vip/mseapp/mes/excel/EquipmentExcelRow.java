package vip.mseapp.mes.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 设备导入/导出行（EasyExcel）：导入模板与导出文件同结构。
 *
 * <p>工位填工位编码（可空）；状态填中文（运行/待机/维修/停机，空默认待机）；购置日期 yyyy-MM-dd。
 */
@Data
public class EquipmentExcelRow {

    @ExcelProperty(value = "设备编码*")
    @ColumnWidth(18)
    private String code;

    @ExcelProperty(value = "设备名称*")
    @ColumnWidth(20)
    private String name;

    @ExcelProperty(value = "规格型号")
    @ColumnWidth(20)
    private String model;

    /** mes_model 工位(type=4)编码，可空 */
    @ExcelProperty(value = "所在工位编码")
    @ColumnWidth(18)
    private String workstationCode;

    /** 运行/待机/维修/停机（空默认待机） */
    @ExcelProperty(value = "状态")
    @ColumnWidth(10)
    private String status;

    /** yyyy-MM-dd */
    @ExcelProperty(value = "购置日期")
    @ColumnWidth(14)
    private String buyDate;

    @ExcelProperty(value = "备注")
    @ColumnWidth(24)
    private String remark;
}
