package vip.mseapp.mes.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.result.Result;
import vip.mseapp.mes.entity.MesCalendar;
import vip.mseapp.mes.mapper.MesCalendarMapper;
import vip.mseapp.mes.service.BatchOpResult;
import vip.mseapp.mes.service.MesBatchRunner;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历与班次（按月查询、批量保存、删除，支持批量删除）。
 */
@Validated
@RestController
@RequestMapping("/mes/calendar")
public class MesCalendarController {

    private final MesCalendarMapper mapper;
    private final MesBatchRunner batchRunner;

    public MesCalendarController(MesCalendarMapper mapper, MesBatchRunner batchRunner) {
        this.mapper = mapper;
        this.batchRunner = batchRunner;
    }

    /** 按月查询（month=yyyy-MM），可按产线过滤 */
    @SaCheckPermission(value = "mes:calendar:list", type = MseConstants.LOGIN_TYPE_TENANT)
    @GetMapping("/list")
    public Result<List<MesCalendar>> list(@RequestParam String month,
                                          @RequestParam(required = false) Long lineId) {
        LocalDate first = LocalDate.parse(month + "-01");
        LocalDate last = first.plusMonths(1).minusDays(1);
        return Result.ok(mapper.selectList(new LambdaQueryWrapper<MesCalendar>()
                .ge(MesCalendar::getWorkDate, first)
                .le(MesCalendar::getWorkDate, last)
                .eq(lineId != null, MesCalendar::getLineId, lineId)
                .orderByAsc(MesCalendar::getWorkDate)
                .orderByAsc(MesCalendar::getShiftCode)));
    }

    /** 批量保存（覆盖式：同日期同班次编码同产线先删后插） */
    @SaCheckPermission(value = "mes:calendar:save", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch")
    public Result<Void> saveBatch(@RequestBody @Validated List<CalendarItem> items) {
        for (CalendarItem item : items) {
            LambdaQueryWrapper<MesCalendar> del = new LambdaQueryWrapper<MesCalendar>()
                    .eq(MesCalendar::getWorkDate, item.workDate())
                    .eq(MesCalendar::getShiftCode, item.shiftCode());
            if (item.lineId() == null) {
                del.isNull(MesCalendar::getLineId);
            } else {
                del.eq(MesCalendar::getLineId, item.lineId());
            }
            mapper.delete(del);
            MesCalendar row = new MesCalendar();
            row.setWorkDate(LocalDate.parse(item.workDate()));
            row.setShiftCode(item.shiftCode());
            row.setShiftName(item.shiftName());
            row.setStartTime(item.startTime() == null || item.startTime().isBlank()
                    ? null : java.time.LocalTime.parse(item.startTime()));
            row.setEndTime(item.endTime() == null || item.endTime().isBlank()
                    ? null : java.time.LocalTime.parse(item.endTime()));
            row.setType(item.type());
            row.setLineId(item.lineId());
            mapper.insert(row);
        }
        return Result.ok();
    }

    @SaCheckPermission(value = "mes:calendar:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    /** 批量删除 */
    @SaCheckPermission(value = "mes:calendar:delete", type = MseConstants.LOGIN_TYPE_TENANT)
    @PostMapping("/batch-delete")
    public Result<BatchOpResult> deleteBatch(@RequestBody @jakarta.validation.constraints.NotEmpty List<Long> ids) {
        return Result.ok(batchRunner.run(ids, mapper::deleteById));
    }

    public record CalendarItem(@NotBlank String workDate, @NotBlank String shiftCode, @NotBlank String shiftName,
                               String startTime, String endTime, @NotNull Integer type, Long lineId) {
    }
}
