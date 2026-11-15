package vip.mseapp.mes.controller;

import com.alibaba.excel.EasyExcel;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 导出响应写出（EasyExcel xlsx，导入模板=空数据导出）。
 */
final class MesExcelWriter {

    private MesExcelWriter() {
    }

    static <T> void write(HttpServletResponse response, String fileName, String sheetName,
                          Class<T> head, List<T> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encoded + ".xlsx");
        EasyExcel.write(response.getOutputStream(), head).sheet(sheetName).doWrite(rows);
    }
}
