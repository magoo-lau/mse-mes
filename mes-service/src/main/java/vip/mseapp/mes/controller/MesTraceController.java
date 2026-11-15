package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesMaterial;
import vip.mseapp.mes.entity.MesSn;
import vip.mseapp.mes.mapper.MesMaterialMapper;
import vip.mseapp.mes.mapper.MesSnMapper;
import vip.mseapp.mes.service.MesTraceService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 追溯：批次正反向追溯、序列号管理与标签打印数据。
 */
@Validated
@RestController
@RequestMapping("/mes/trace")
public class MesTraceController {

    private final MesTraceService traceService;
    private final MesSnMapper snMapper;
    private final MesMaterialMapper materialMapper;

    public MesTraceController(MesTraceService traceService, MesSnMapper snMapper, MesMaterialMapper materialMapper) {
        this.traceService = traceService;
        this.snMapper = snMapper;
        this.materialMapper = materialMapper;
    }

    @SaCheckPermission(value = "mes:trace:query", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/forward")
    public Result<Map<String, Object>> forward(@RequestParam @NotBlank String batchNo) {
        return Result.ok(traceService.forward(batchNo));
    }

    @SaCheckPermission(value = "mes:trace:query", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/backward")
    public Result<Map<String, Object>> backward(@RequestParam @NotBlank String batchNo) {
        return Result.ok(traceService.backward(batchNo));
    }

    /** 追溯报告导出（CSV：批次主档 + 链路摘要） */
    @SaCheckPermission(value = "mes:trace:export", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/export")
    public Result<String> export(@RequestParam @NotBlank String batchNo) {
        Map<String, Object> data = traceService.backward(batchNo);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("追溯报告（反向）\n批次号,%s\n物料,%s\n数量,%s\n\n工序报工\n报工单,工序,合格,不良,报工时间\n"
                .formatted(data.get("batchNo"), data.get("materialName"), String.valueOf(data.get("qty"))));
        Object reports = data.get("reports");
        if (reports instanceof Iterable<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    csv.append(map.get("reportCode")).append(',').append(map.get("stepName")).append(',')
                            .append(map.get("okQty")).append(',').append(map.get("ngQty")).append(',')
                            .append(map.get("reportTime")).append('\n');
                }
            }
        }
        csv.append("\n耗用原料\n领料单,物料,批次,数量\n");
        Object materials = data.get("materials");
        if (materials instanceof Iterable<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map) {
                    csv.append(map.get("code")).append(',').append(map.get("refLabel")).append(',')
                            .append(map.get("refLabel")).append(',').append(map.get("qty")).append('\n');
                }
            }
        }
        csv.append("\n导出时间,").append(LocalDateTime.now()).append('\n');
        return Result.ok(csv.toString());
    }

    @SaCheckPermission(value = "mes:sn:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/sn/page")
    public Result<PageResult<MesSn>> snPage(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String keyword) {
        Page<MesSn> page = snMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MesSn>()
                        .like(keyword != null && !keyword.isBlank(), MesSn::getSn, keyword)
                        .orderByDesc(MesSn::getId));
        return Result.ok(PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords()));
    }

    @SaCheckPermission(value = "mes:sn:bind", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/sn/bind")
    public Result<Long> snBind(@RequestBody @Validated SnBindRequest request) {
        return Result.ok(traceService.snBind(request.sn(), request.materialId(), request.orderId(),
                request.batchNo(), request.stepId(), request.status()));
    }

    /** 标签打印数据（物料标签/批次标签/序列号标签，前端套模板渲染或对接打印机） */
    @SaCheckPermission(value = "mes:label:print", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/label/print")
    public Result<Map<String, Object>> labelPrint(@RequestBody @Validated LabelRequest request) {
        Map<String, Object> label = new HashMap<>();
        label.put("type", request.type());
        MesMaterial material = request.materialId() == null ? null
                : materialMapper.selectById(request.materialId());
        label.put("materialCode", material == null ? null : material.getCode());
        label.put("materialName", material == null ? null : material.getName());
        label.put("spec", material == null ? null : material.getSpec());
        label.put("batchNo", request.batchNo());
        label.put("sn", request.sn());
        label.put("printTime", LocalDateTime.now());
        label.put("copies", request.copies() == null ? 1 : request.copies());
        return Result.ok(label);
    }

    public record SnBindRequest(@NotBlank String sn, @NotNull Long materialId, Long orderId,
                                String batchNo, Long stepId, Integer status) {
    }

    public record LabelRequest(@NotBlank String type, Long materialId, String batchNo, String sn, Integer copies) {
    }
}
