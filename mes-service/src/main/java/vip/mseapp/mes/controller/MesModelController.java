package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesModel;
import vip.mseapp.mes.excel.ModelExcelRow;
import vip.mseapp.mes.mapper.MesModelMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.util.List;

/**
 * 制造建模（工厂/车间/产线/工位四级树：CRUD + 批量删除 + Excel 导入/导出/模板下载）。
 */
@Validated
@RestController
@RequestMapping("/mes/model")
public class MesModelController {

    private final MesModelMapper mapper;
    private final MesMasterDataService masterDataService;

    public MesModelController(MesModelMapper mapper, MesMasterDataService masterDataService) {
        this.mapper = mapper;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:model:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesModel>> list(@RequestParam(required = false) Integer type,
                                       @RequestParam(required = false) Long parentId) {
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<MesModel>()
                .eq(type != null, MesModel::getType, type)
                .eq(parentId != null, MesModel::getParentId, parentId)
                .orderByAsc(MesModel::getType)
                .orderByAsc(MesModel::getCode)));
    }

    @SaCheckPermission(value = "mes:model:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated SaveRequest request) {
        masterDataService.requireModelCodeUnique(request.code(), null);
        MesModel model = new MesModel();
        apply(model, request);
        mapper.insert(model);
        return Result.ok(model.getId());
    }

    @SaCheckPermission(value = "mes:model:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated SaveRequest request) {
        MesModel model = mapper.selectById(id);
        if (model == null) {
            throw new ServiceException(1404, "建模节点不存在");
        }
        masterDataService.requireModelCodeUnique(request.code(), id);
        apply(model, request);
        mapper.updateById(model);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:model:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteModelChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：有下级或被工序/日历/设备引用的节点跳过并报原因） */
    @SaCheckPermission(value = "mes:model:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteModelsBatch(ids));
    }

    // -------------------- Excel 导入导出（模板与导出同结构；导出全量树保证上级编码可回导） --------------------

    @OperLog(module = "mes:model", type = "导出")
    @SaCheckPermission(value = "mes:model:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "制造建模", "建模", ModelExcelRow.class,
                masterDataService.modelExportRows());
    }

    @OperLog(module = "mes:model", type = "导入")
    @SaCheckPermission(value = "mes:model:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<ModelExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(ModelExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importModels(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:model:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "制造建模导入模板", "建模", ModelExcelRow.class, List.of());
    }

    private void apply(MesModel model, SaveRequest request) {
        model.setType(request.type());
        model.setParentId(request.parentId() == null ? 0L : request.parentId());
        model.setCode(request.code());
        model.setName(request.name());
        model.setRemark(request.remark());
    }

    public record SaveRequest(@NotNull Integer type, Long parentId, @NotBlank String code,
                              @NotBlank String name, String remark) {
    }
}
