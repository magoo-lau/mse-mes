package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesBom;
import vip.mseapp.mes.entity.MesBomLine;
import vip.mseapp.mes.excel.BomExcelRow;
import vip.mseapp.mes.mapper.MesBomLineMapper;
import vip.mseapp.mes.mapper.MesBomMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * BOM 管理（头 + 明细整体保存 + 批量删除 + Excel 导入/导出/模板下载，导入一行一个子件按 BOM 编码归组）。
 */
@Validated
@RestController
@RequestMapping("/mes/bom")
public class MesBomController {

    private final MesBomMapper bomMapper;
    private final MesBomLineMapper lineMapper;
    private final MesMasterDataService masterDataService;

    public MesBomController(MesBomMapper bomMapper, MesBomLineMapper lineMapper,
                            MesMasterDataService masterDataService) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:bom:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesBom>> page(@RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "10") long pageSize,
                                           @RequestParam(required = false) Long materialId) {
        Page<MesBom> page = bomMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesBom>()
                        .eq(materialId != null, MesBom::getMaterialId, materialId)
                        .orderByDesc(MesBom::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 详情（含明细行，明细按 id 升序） */
    @SaCheckPermission(value = "mes:bom:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MesBom bom = bomMapper.selectById(id);
        if (bom == null) {
            throw new ServiceException(1404, "BOM 不存在");
        }
        List<MesBomLine> lines = lineMapper.selectList(new LambdaQueryWrapper<MesBomLine>()
                .eq(MesBomLine::getBomId, id).orderByAsc(MesBomLine::getId));
        return Result.ok(Map.of("bom", bom, "lines", lines));
    }

    /** 保存（新建或更新，明细全量重建） */
    @SaCheckPermission(value = "mes:bom:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> save(@RequestBody @Validated BomSaveRequest request) {
        MesBom bom;
        if (request.id() == null) {
            masterDataService.requireBomCodeUnique(request.code(), null);
            bom = new MesBom();
            bom.setCode(request.code());
        } else {
            bom = bomMapper.selectById(request.id());
            if (bom == null) {
                throw new ServiceException(1404, "BOM 不存在");
            }
        }
        bom.setMaterialId(request.materialId());
        bom.setVersion(request.version());
        bom.setRemark(request.remark());
        if (request.id() == null) {
            bomMapper.insert(bom);
        } else {
            bomMapper.updateById(bom);
            lineMapper.delete(new LambdaQueryWrapper<MesBomLine>().eq(MesBomLine::getBomId, bom.getId()));
        }
        for (LineItem line : request.lines() == null ? List.<LineItem>of() : request.lines()) {
            MesBomLine row = new MesBomLine();
            row.setBomId(bom.getId());
            row.setMaterialId(line.materialId());
            row.setQty(line.qty());
            row.setLossRate(line.lossRate());
            lineMapper.insert(row);
        }
        return Result.ok(bom.getId());
    }

    @SaCheckPermission(value = "mes:bom:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteBomChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：被工单引用的 BOM 跳过并报原因） */
    @SaCheckPermission(value = "mes:bom:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteBomsBatch(ids));
    }

    // -------------------- Excel 导入导出（一行一个子件行，模板与导出同结构） --------------------

    @OperLog(module = "mes:bom", type = "导出")
    @SaCheckPermission(value = "mes:bom:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "BOM数据", "BOM", BomExcelRow.class,
                masterDataService.bomExportRows());
    }

    @OperLog(module = "mes:bom", type = "导入")
    @SaCheckPermission(value = "mes:bom:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<BomExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(BomExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importBoms(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:bom:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "BOM导入模板", "BOM", BomExcelRow.class, List.of());
    }

    public record BomSaveRequest(Long id, @NotBlank String code, @NotNull Long materialId,
                                 String version, String remark, List<LineItem> lines) {
    }

    public record LineItem(Long materialId, java.math.BigDecimal qty, java.math.BigDecimal lossRate) {
    }
}
