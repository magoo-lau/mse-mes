package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesMaterial;
import vip.mseapp.mes.excel.MaterialExcelRow;
import vip.mseapp.mes.mapper.MesMaterialMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.util.List;

/**
 * 物料主数据（租户端 CRUD + 批量删除 + Excel 导入/导出/模板下载）。
 */
@Validated
@RestController
@RequestMapping("/mes/material")
public class MesMaterialController {

    private final MesMaterialMapper mapper;
    private final MesMasterDataService masterDataService;

    public MesMaterialController(MesMaterialMapper mapper, MesMasterDataService masterDataService) {
        this.mapper = mapper;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:material:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesMaterial>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer type) {
        Page<MesMaterial> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesMaterial>()
                        .and(keyword != null && !keyword.isBlank(),
                                w -> w.like(MesMaterial::getName, keyword).or().like(MesMaterial::getCode, keyword))
                        .eq(type != null, MesMaterial::getType, type)
                        .orderByDesc(MesMaterial::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:material:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesMaterial>> list() {
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<MesMaterial>().orderByAsc(MesMaterial::getCode)));
    }

    @SaCheckPermission(value = "mes:material:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated SaveRequest request) {
        masterDataService.requireMaterialCodeUnique(request.code(), null);
        MesMaterial material = new MesMaterial();
        apply(material, request);
        mapper.insert(material);
        return Result.ok(material.getId());
    }

    @SaCheckPermission(value = "mes:material:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated SaveRequest request) {
        MesMaterial material = mapper.selectById(id);
        if (material == null) {
            throw new ServiceException(1404, "物料不存在");
        }
        masterDataService.requireMaterialCodeUnique(request.code(), id);
        apply(material, request);
        mapper.updateById(material);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:material:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteMaterialChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：被 BOM/工艺路线/检验方案/工单引用的物料跳过并报原因） */
    @SaCheckPermission(value = "mes:material:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteMaterialsBatch(ids));
    }

    // -------------------- Excel 导入导出（模板与导出同结构） --------------------

    @OperLog(module = "mes:material", type = "导出")
    @SaCheckPermission(value = "mes:material:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword) throws IOException {
        MesExcelWriter.write(response, "物料数据", "物料", MaterialExcelRow.class,
                masterDataService.materialExportRows(keyword));
    }

    @OperLog(module = "mes:material", type = "导入")
    @SaCheckPermission(value = "mes:material:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<MaterialExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(MaterialExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importMaterials(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:material:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "物料导入模板", "物料", MaterialExcelRow.class, List.of());
    }

    private void apply(MesMaterial material, SaveRequest request) {
        material.setCode(request.code());
        material.setName(request.name());
        material.setSpec(request.spec());
        material.setUnit(request.unit());
        material.setType(request.type());
        material.setTraceMode(request.traceMode());
        material.setRemark(request.remark());
    }

    public record SaveRequest(@NotBlank String code, @NotBlank String name, String spec, String unit,
                              Integer type, Integer traceMode, String remark) {
    }
}
