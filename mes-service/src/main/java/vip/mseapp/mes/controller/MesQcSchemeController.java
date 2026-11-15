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
import vip.mseapp.mes.entity.MesQcScheme;
import vip.mseapp.mes.entity.MesQcSchemeItem;
import vip.mseapp.mes.excel.QcSchemeExcelRow;
import vip.mseapp.mes.mapper.MesQcSchemeItemMapper;
import vip.mseapp.mes.mapper.MesQcSchemeMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.mes.service.MesQcService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 检验方案管理（头 + 检验项目整体保存 + 批量删除 + Excel 导入/导出/模板下载，导入一行一个检验项按方案编码归组）。
 */
@Validated
@RestController
@RequestMapping("/mes/qc/scheme")
public class MesQcSchemeController {

    private final MesQcSchemeMapper schemeMapper;
    private final MesQcSchemeItemMapper itemMapper;
    private final MesQcService qcService;
    private final MesMasterDataService masterDataService;

    public MesQcSchemeController(MesQcSchemeMapper schemeMapper, MesQcSchemeItemMapper itemMapper,
                                 MesQcService qcService, MesMasterDataService masterDataService) {
        this.schemeMapper = schemeMapper;
        this.itemMapper = itemMapper;
        this.qcService = qcService;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:qc:scheme:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesQcScheme>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) Integer qcType) {
        Page<MesQcScheme> page = schemeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesQcScheme>()
                        .eq(qcType != null, MesQcScheme::getQcType, qcType)
                        .orderByDesc(MesQcScheme::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 详情（含检验项目） */
    @SaCheckPermission(value = "mes:qc:scheme:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MesQcScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new ServiceException(1404, "检验方案不存在");
        }
        List<MesQcSchemeItem> items = itemMapper.selectList(new LambdaQueryWrapper<MesQcSchemeItem>()
                .eq(MesQcSchemeItem::getSchemeId, id).orderByAsc(MesQcSchemeItem::getId));
        return Result.ok(Map.of("scheme", scheme, "items", items));
    }

    @SaCheckPermission(value = "mes:qc:scheme:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> save(@RequestBody @Validated SchemeSaveRequest request) {
        masterDataService.requireQcSchemeCodeUnique(request.code(), request.id());
        return Result.ok(qcService.saveScheme(request.id(), request.code(), request.name(), request.qcType(),
                request.materialId(), request.sampleRule(), request.remark(),
                request.items() == null ? List.of()
                        : request.items().stream()
                                .map(i -> new MesQcService.SchemeItem(i.itemName(), i.standard(), i.method()))
                                .toList()));
    }

    @SaCheckPermission(value = "mes:qc:scheme:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteQcSchemeChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：被检验单引用的方案跳过并报原因） */
    @SaCheckPermission(value = "mes:qc:scheme:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteQcSchemesBatch(ids));
    }

    // -------------------- Excel 导入导出（一行一个检验项，模板与导出同结构） --------------------

    @OperLog(module = "mes:qc:scheme", type = "导出")
    @SaCheckPermission(value = "mes:qc:scheme:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "检验方案", "检验方案", QcSchemeExcelRow.class,
                masterDataService.qcSchemeExportRows());
    }

    @OperLog(module = "mes:qc:scheme", type = "导入")
    @SaCheckPermission(value = "mes:qc:scheme:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<QcSchemeExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(QcSchemeExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importQcSchemes(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:qc:scheme:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "检验方案导入模板", "检验方案", QcSchemeExcelRow.class, List.of());
    }

    public record SchemeSaveRequest(Long id, @NotBlank String code, @NotBlank String name,
                                    @NotNull Integer qcType, Long materialId, String sampleRule,
                                    String remark, List<ItemPayload> items) {
    }

    public record ItemPayload(String itemName, String standard, String method) {
    }
}
