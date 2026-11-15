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
import vip.mseapp.mes.entity.MesRoute;
import vip.mseapp.mes.entity.MesRouteStep;
import vip.mseapp.mes.excel.RouteExcelRow;
import vip.mseapp.mes.mapper.MesRouteMapper;
import vip.mseapp.mes.mapper.MesRouteStepMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesMasterDataService;
import vip.mseapp.web.log.OperLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 工艺路线管理（头 + 工序整体保存 + 批量删除 + Excel 导入/导出/模板下载，导入一行一个工序按路线编码归组）。
 */
@Validated
@RestController
@RequestMapping("/mes/route")
public class MesRouteController {

    private final MesRouteMapper routeMapper;
    private final MesRouteStepMapper stepMapper;
    private final MesMasterDataService masterDataService;

    public MesRouteController(MesRouteMapper routeMapper, MesRouteStepMapper stepMapper,
                              MesMasterDataService masterDataService) {
        this.routeMapper = routeMapper;
        this.stepMapper = stepMapper;
        this.masterDataService = masterDataService;
    }

    @SaCheckPermission(value = "mes:route:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/page")
    public Result<PageResult<MesRoute>> page(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) Long materialId,
                                             @RequestParam(required = false) Integer status) {
        Page<MesRoute> page = routeMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesRoute>()
                        .eq(materialId != null, MesRoute::getMaterialId, materialId)
                        .eq(status != null, MesRoute::getStatus, status)
                        .orderByDesc(MesRoute::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    /** 详情（含工序列表，按工序号升序） */
    @SaCheckPermission(value = "mes:route:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        MesRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new ServiceException(1404, "工艺路线不存在");
        }
        List<MesRouteStep> steps = stepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getRouteId, id).orderByAsc(MesRouteStep::getStepNo));
        return Result.ok(Map.of("route", route, "steps", steps));
    }

    /** 保存（草稿可改；已发布需先停用回草稿），工序全量重建 */
    @SaCheckPermission(value = "mes:route:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping
    public Result<Long> save(@RequestBody @Validated RouteSaveRequest request) {
        MesRoute route;
        if (request.id() == null) {
            masterDataService.requireRouteCodeUnique(request.code(), null);
            route = new MesRoute();
            route.setCode(request.code());
            route.setStatus(0);
        } else {
            route = routeMapper.selectById(request.id());
            if (route == null) {
                throw new ServiceException(1404, "工艺路线不存在");
            }
            if (route.getStatus() != null && route.getStatus() == 1) {
                throw new ServiceException(1409, "已发布的工艺路线不能修改，请先停用");
            }
        }
        route.setName(request.name());
        route.setMaterialId(request.materialId());
        route.setVersion(request.version());
        route.setRemark(request.remark());
        if (request.id() == null) {
            routeMapper.insert(route);
        } else {
            routeMapper.updateById(route);
            stepMapper.delete(new LambdaQueryWrapper<MesRouteStep>().eq(MesRouteStep::getRouteId, route.getId()));
        }
        int no = 1;
        for (StepItem step : request.steps() == null ? List.<StepItem>of() : request.steps()) {
            MesRouteStep row = new MesRouteStep();
            row.setRouteId(route.getId());
            row.setStepNo(no++);
            row.setStepName(step.stepName());
            row.setLineId(step.lineId());
            row.setWorkstationId(step.workstationId());
            row.setStandardTime(step.standardTime());
            row.setQcRequired(Boolean.TRUE.equals(step.qcRequired()) ? 1 : 0);
            stepMapper.insert(row);
        }
        return Result.ok(route.getId());
    }

    /** 发布/停用（status: 1 发布 0 停用回草稿） */
    @SaCheckPermission(value = "mes:route:publish", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/{id}/status/{status}")
    public Result<Void> publish(@PathVariable Long id, @PathVariable Integer status) {
        MesRoute route = routeMapper.selectById(id);
        if (route == null) {
            throw new ServiceException(1404, "工艺路线不存在");
        }
        long stepCount = stepMapper.selectCount(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getRouteId, id));
        if (status == 1 && stepCount == 0) {
            throw new ServiceException(1409, "无工序的工艺路线不能发布");
        }
        route.setStatus(status);
        routeMapper.updateById(route);
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:route:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        masterDataService.deleteRouteChecked(id);
        return Result.ok();
    }

    /** 批量删除（部分成功：已发布或被工单引用的路线跳过并报原因） */
    @SaCheckPermission(value = "mes:route:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(masterDataService.deleteRoutesBatch(ids));
    }

    // -------------------- Excel 导入导出（一行一个工序，模板与导出同结构） --------------------

    @OperLog(module = "mes:route", type = "导出")
    @SaCheckPermission(value = "mes:route:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "工艺路线", "工艺路线", RouteExcelRow.class,
                masterDataService.routeExportRows());
    }

    @OperLog(module = "mes:route", type = "导入")
    @SaCheckPermission(value = "mes:route:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/import")
    public Result<BatchOpResult> importExcel(@RequestParam("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean updateSupport)
            throws IOException {
        List<RouteExcelRow> rows = EasyExcel.read(file.getInputStream())
                .head(RouteExcelRow.class).sheet().doReadSync();
        return Result.ok(masterDataService.importRoutes(rows, updateSupport));
    }

    @SaCheckPermission(value = "mes:route:import", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        MesExcelWriter.write(response, "工艺路线导入模板", "工艺路线", RouteExcelRow.class, List.of());
    }

    public record RouteSaveRequest(Long id, @NotBlank String code, @NotBlank String name,
                                   Long materialId, String version, String remark, List<StepItem> steps) {
    }

    public record StepItem(@NotBlank String stepName, Long lineId, Long workstationId,
                           Integer standardTime, Boolean qcRequired) {
    }
}
