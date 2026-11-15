package vip.mseapp.mes.service;

import vip.mseapp.mes.entity.MesCheckRecord;
import vip.mseapp.mes.entity.MesEquipment;
import vip.mseapp.mes.entity.MesRepair;
import vip.mseapp.mes.mapper.MesCheckRecordMapper;
import vip.mseapp.mes.mapper.MesEquipmentMapper;
import vip.mseapp.mes.mapper.MesRepairMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 设备业务：点检执行（异常自动转报修）、报修/维修/完工（联动设备状态与停机时长）。
 */
@Service
public class MesEquipmentService {

    /** 设备状态：1 运行 2 待机 3 维修 4 停机 */
    public static final int EQ_RUNNING = 1, EQ_IDLE = 2, EQ_REPAIR = 3, EQ_DOWN = 4;

    private final MesEquipmentMapper equipmentMapper;
    private final MesCheckRecordMapper checkRecordMapper;
    private final MesRepairMapper repairMapper;
    private final MesCodeGenerator codeGenerator;

    public MesEquipmentService(MesEquipmentMapper equipmentMapper, MesCheckRecordMapper checkRecordMapper,
                               MesRepairMapper repairMapper, MesCodeGenerator codeGenerator) {
        this.equipmentMapper = equipmentMapper;
        this.checkRecordMapper = checkRecordMapper;
        this.repairMapper = repairMapper;
        this.codeGenerator = codeGenerator;
    }

    /** 点检/保养执行：异常结果自动生成维修工单并置设备维修中 */
    @Transactional(rollbackFor = Exception.class)
    public void checkSubmit(Long planId, Long equipmentId, Integer result, String remark) {
        // 设备存在性校验：此前仅异常转报修路径校验，正常打卡可对不存在设备落记录（孤儿数据）
        if (equipmentMapper.selectById(equipmentId) == null) {
            throw new ServiceException(1404, "设备不存在");
        }
        MesCheckRecord record = new MesCheckRecord();
        record.setPlanId(planId);
        record.setEquipmentId(equipmentId);
        record.setCheckDate(java.time.LocalDate.now());
        record.setResult(result);
        record.setCheckerId(UserContext.getUserId());
        record.setCheckTime(LocalDateTime.now());
        record.setRemark(remark);
        checkRecordMapper.insert(record);
        if (result != null && result == 2) {
            createRepair(equipmentId, record.getId(), "点检/保养异常：" + (remark == null ? "" : remark), null);
        }
    }

    /** 报修（设备转维修中） */
    @Transactional(rollbackFor = Exception.class)
    public Long repairReport(Long equipmentId, String faultDesc) {
        return createRepair(equipmentId, null, faultDesc, UserContext.getUserId());
    }

    private Long createRepair(Long equipmentId, Long checkRecordId, String faultDesc, Long reportBy) {
        MesEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw new ServiceException(1404, "设备不存在");
        }
        MesRepair repair = new MesRepair();
        repair.setCode(codeGenerator.next("RP"));
        repair.setEquipmentId(equipmentId);
        repair.setCheckRecordId(checkRecordId);
        repair.setFaultDesc(faultDesc);
        repair.setReportBy(reportBy);
        repair.setReportTime(LocalDateTime.now());
        repair.setStatus(0);
        repairMapper.insert(repair);
        equipment.setStatus(EQ_REPAIR);
        equipmentMapper.updateById(equipment);
        return repair.getId();
    }

    /** 维修处理：登记维修人转维修中 */
    @Transactional(rollbackFor = Exception.class)
    public void repairTake(Long id, Long repairBy) {
        MesRepair repair = requireRepair(id);
        if (repair.getStatus() != 0) {
            throw new ServiceException(1409, "该维修单已受理");
        }
        repair.setRepairBy(repairBy == null ? UserContext.getUserId() : repairBy);
        repair.setStatus(1);
        repairMapper.updateById(repair);
    }

    /** 维修完工：停机时长自动累计，设备恢复待机 */
    @Transactional(rollbackFor = Exception.class)
    public void repairFinish(Long id, String handleDesc) {
        MesRepair repair = requireRepair(id);
        if (repair.getStatus() == 2) {
            throw new ServiceException(1409, "该维修单已完工");
        }
        LocalDateTime now = LocalDateTime.now();
        repair.setHandleDesc(handleDesc);
        repair.setFinishTime(now);
        repair.setDowntimeMinutes(Duration.between(repair.getReportTime(), now).toMinutes());
        repair.setStatus(2);
        repairMapper.updateById(repair);
        MesEquipment equipment = equipmentMapper.selectById(repair.getEquipmentId());
        if (equipment != null && equipment.getStatus() == EQ_REPAIR) {
            equipment.setStatus(EQ_IDLE);
            equipmentMapper.updateById(equipment);
        }
    }

    private MesRepair requireRepair(Long id) {
        MesRepair repair = repairMapper.selectById(id);
        if (repair == null) {
            throw new ServiceException(1404, "维修工单不存在");
        }
        return repair;
    }
}
