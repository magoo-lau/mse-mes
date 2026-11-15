package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
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
import vip.mseapp.mes.entity.MesIotCollect;
import vip.mseapp.mes.iot.MesIotService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备数采监控（三期，菜单 512）：网关/模拟上报（登录态）、实时状态看板、OEE、数采明细、设备配置。
 * 权限点 4482 mes:iot:collect:list（挂 512，覆盖查询/看板/明细/配置）。
 */
@RestController
@Validated
@RequestMapping("/mes/iot")
public class MesIotController {

    private final MesIotService iotService;

    public MesIotController(MesIotService iotService) {
        this.iotService = iotService;
    }

    /** 数采上报（IoT 网关经服务账号调用；页面「模拟上报」同通道，登录即可） */
    @SaCheckLogin(type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestBody @Valid CollectRequest request) {
        return Result.ok(iotService.collect(request.equipmentCode(), request.status(),
                request.outputQty(), request.collectTime()));
    }

    /** 实时状态看板（窗口内有数采=在线实时源，否则回退台账状态） */
    @SaCheckPermission(value = "mes:iot:collect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/realtime")
    public Result<List<Map<String, Object>>> realtime() {
        return Result.ok(iotService.realtime());
    }

    /** OEE 统计（近 N 天，完整口径：可用率×性能×质量） */
    @SaCheckPermission(value = "mes:iot:collect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/oee")
    public Result<List<Map<String, Object>>> oee(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(iotService.oee(days));
    }

    /** 数采明细分页 */
    @SaCheckPermission(value = "mes:iot:collect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/collect/page")
    public Result<PageResult<MesIotCollect>> pageCollects(@RequestParam(defaultValue = "1") long pageNum,
                                                          @RequestParam(defaultValue = "10") long pageSize,
                                                          @RequestParam(required = false) Long equipmentId,
                                                          @RequestParam(required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime begin,
                                                          @RequestParam(required = false)
                                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(iotService.pageCollects(pageNum, pageSize, equipmentId, begin, end));
    }

    /** 设备数采配置清单 */
    @SaCheckPermission(value = "mes:iot:collect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/config")
    public Result<List<Map<String, Object>>> config() {
        return Result.ok(iotService.configList());
    }

    /** 保存设备数采配置（额定产能/启用） */
    @SaCheckPermission(value = "mes:iot:collect:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/config")
    public Result<Void> saveConfig(@RequestBody @Valid ConfigSaveRequest request) {
        iotService.saveConfig(request.equipmentId(), request.ratedCapacity(), request.enabled());
        return Result.ok();
    }

    public record CollectRequest(@NotBlank(message = "设备编码不能为空") String equipmentCode,
                                 Integer status,
                                 java.math.BigDecimal outputQty,
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime collectTime) {
    }

    public record ConfigSaveRequest(Long equipmentId, java.math.BigDecimal ratedCapacity, Integer enabled) {
    }
}
