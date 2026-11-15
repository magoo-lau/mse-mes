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
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.excel.EquipmentExcelRow;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 设备台账与状态（CRUD + 批量删除 + Excel 导入/导出/模板下载）。
 */
@Validated
@RestController
@RequestMapping("/mes/equipment")
public class MesEquipmentController {

    private final MesEquipmentMapper mapper;
    private final MesMasterDataService masterDataService;

    public MesEquipmentController(MesEquipmentMapper mapper, MesMasterDataService masterDataService) {
        this.mapper = mapper;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:equip:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesEquipment>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String keyword) {
        Page<MesEquipment> page = mapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesEquipment>()
                        .and(keyword != null && !keyword.isBlank(),
                                w -> w.like(MesEquipment::getName, keyword).or().like(MesEquipment::getCode, keyword))
                        .orderByDesc(MesEquipment::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:equip:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesEquipment>> list() {
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<MesEquipment>().orderByAsc(MesEquipment::getCode)));
    }

    @SaCheckPermission(value = "mes:equip:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> create(@RequestBody @Validated SaveRequest request) {
        masterDataService.requireEquipmentCodeUnique(request.code(), null);
        MesEquipment equipment = new MesEquipment();
        apply(equipment, request);
        mapper.insert(equipment);
        return Result.ok(equipment.getId());
    }

    @SaCheckPermission(value = "mes:equip:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Validated SaveRequest request) {
        MesEquipment equipment = mapper.selectById(id);
        if (equipment == null) {
            throw new ServiceException(1404, "设备不存在");
        }
        masterDataService.requireEquipmentCodeUnique(request.code(), id);
        apply(equipment, request);
        mapper.updateById(equipment);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:equip:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteEquipmentChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：被点检计划/维修工单/数采配置引用的设备跳过并报原因） */
    @SaCheckPermission(value = "mes:equip:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteEquipmentsBatch(ids));
    }

    // -------------------- Excel 导入导出（模板与导出同结构） --------------------

    @OperLog(module = "mes:equip", type = "导出")
    @SaCheckPermission(value = "mes:equip:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword) throws IOException {
        MesExcelWriter.write(response, "设备台账", "设备", EquipmentExcelRow.class,
                masterDataService.equipmentExportRows(keyword));
    }

    @OperLog(module = "mes:equip", type = "导入")
    @SaCheckPermission(value = "mes:equip:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<EquipmentExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(EquipmentExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importEquipments(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:equip:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "设备导入模板", "设备", EquipmentExcelRow.class, List.of());
    }

    /** 设备状态一览（含按状态计数，看板用） */
    @SaCheckPermission(value = "mes:equip:status:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        List<MesEquipment> all = mapper.selectList(null);
        Map<Integer, Long> counts = new java.util.HashMap<>();
        all.forEach(e -> counts.merge(e.getStatus(), 1L, Long::sum));
        return Result.ok(Map.of("list", all, "counts", counts));
    }

    private void apply(MesEquipment equipment, SaveRequest request) {
        equipment.setCode(request.code());
        equipment.setName(request.name());
        equipment.setModel(request.model());
        equipment.setWorkstationId(request.workstationId());
        equipment.setStatus(request.status() == null ? 2 : request.status());
        equipment.setBuyDate(request.buyDate() == null || request.buyDate().isBlank()
                ? null : LocalDate.parse(request.buyDate()));
        equipment.setRemark(request.remark());
    }

    public record SaveRequest(@NotBlank String code, @NotBlank String name, String model, Long workstationId,
                              Integer status, String buyDate, String remark) {
    }
}
