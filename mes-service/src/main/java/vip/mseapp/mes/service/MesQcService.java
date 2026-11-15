package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesDefect;
import vip.mseapp.mes.entity.MesQcException;
import vip.mseapp.mes.entity.MesQcOrder;
import vip.mseapp.mes.entity.MesQcScheme;
import vip.mseapp.mes.entity.MesQcSchemeItem;
import vip.mseapp.mes.mapper.MesDefectMapper;
import vip.mseapp.mes.mapper.MesQcExceptionMapper;
import vip.mseapp.mes.mapper.MesQcOrderMapper;
import vip.mseapp.mes.mapper.MesQcSchemeItemMapper;
import vip.mseapp.mes.mapper.MesQcSchemeMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 质量业务：检验方案（含明细重建）、检验执行、不良处置、质量异常。
 */
@Service
public class MesQcService {

    private final MesQcSchemeMapper schemeMapper;
    private final MesQcSchemeItemMapper schemeItemMapper;
    private final MesQcOrderMapper qcOrderMapper;
    private final MesDefectMapper defectMapper;
    private final MesQcExceptionMapper exceptionMapper;
    private final MesCodeGenerator codeGenerator;

    public MesQcService(MesQcSchemeMapper schemeMapper, MesQcSchemeItemMapper schemeItemMapper,
                        MesQcOrderMapper qcOrderMapper, MesDefectMapper defectMapper,
                        MesQcExceptionMapper exceptionMapper, MesCodeGenerator codeGenerator) {
        this.schemeMapper = schemeMapper;
        this.schemeItemMapper = schemeItemMapper;
        this.qcOrderMapper = qcOrderMapper;
        this.defectMapper = defectMapper;
        this.exceptionMapper = exceptionMapper;
        this.codeGenerator = codeGenerator;
    }

    /** 保存检验方案（头 + 明细全量重建）；id 为空则新建 */
    @Transactional(rollbackFor = Exception.class)
    public Long saveScheme(Long id, String code, String name, Integer qcType, Long materialId,
                           String sampleRule, String remark, List<SchemeItem> items) {
        MesQcScheme scheme;
        if (id == null) {
            scheme = new MesQcScheme();
            scheme.setCode(code);
        } else {
            scheme = schemeMapper.selectById(id);
            if (scheme == null) {
                throw new ServiceException(1404, "检验方案不存在");
            }
        }
        scheme.setName(name);
        scheme.setQcType(qcType);
        scheme.setMaterialId(materialId);
        scheme.setSampleRule(sampleRule);
        scheme.setRemark(remark);
        if (id == null) {
            schemeMapper.insert(scheme);
        } else {
            schemeMapper.updateById(scheme);
            schemeItemMapper.delete(new LambdaQueryWrapper<MesQcSchemeItem>()
                    .eq(MesQcSchemeItem::getSchemeId, scheme.getId()));
        }
        if (items != null) {
            for (SchemeItem item : items) {
                MesQcSchemeItem row = new MesQcSchemeItem();
                row.setSchemeId(scheme.getId());
                row.setItemName(item.itemName());
                row.setStandard(item.standard());
                row.setMethod(item.method());
                schemeItemMapper.insert(row);
            }
        }
        return scheme.getId();
    }

    /** 检验执行：录入结果；不良自动生成待处置单 */
    @Transactional(rollbackFor = Exception.class)
    public void inspectSubmit(Long qcOrderId, BigDecimal okQty, BigDecimal ngQty, Integer result,
                              String inspectData) {
        MesQcOrder qc = qcOrderMapper.selectById(qcOrderId);
        if (qc == null) {
            throw new ServiceException(1404, "检验单不存在");
        }
        if (qc.getStatus() == 1) {
            throw new ServiceException(1409, "检验单已完成");
        }
        if (okQty == null || ngQty == null || okQty.signum() < 0 || ngQty.signum() < 0) {
            throw new ServiceException(1401, "合格/不良数量必须不小于 0");
        }
        qc.setOkQty(okQty);
        qc.setNgQty(ngQty);
        qc.setResult(result == null ? (ngQty.signum() > 0 ? 2 : 1) : result);
        qc.setInspectData(inspectData);
        qc.setInspectorId(UserContext.getUserId());
        qc.setInspectTime(LocalDateTime.now());
        qc.setStatus(1);
        qcOrderMapper.updateById(qc);
        if (ngQty.signum() > 0) {
            MesDefect defect = new MesDefect();
            defect.setQcOrderId(qc.getId());
            defect.setOrderId(qc.getOrderId());
            defect.setMaterialId(qc.getMaterialId());
            defect.setBatchNo(qc.getBatchNo());
            defect.setQty(ngQty);
            defect.setStatus(0);
            defectMapper.insert(defect);
        }
    }

    /** 不良处置：返工/报废/让步接收 */
    @Transactional(rollbackFor = Exception.class)
    public void defectHandle(Long id, Integer dispose, String disposeRemark) {
        MesDefect defect = defectMapper.selectById(id);
        if (defect == null) {
            throw new ServiceException(1404, "处置单不存在");
        }
        if (defect.getStatus() == 1) {
            throw new ServiceException(1409, "该不良已处置");
        }
        defect.setDispose(dispose);
        defect.setDisposeRemark(disposeRemark);
        defect.setDisposeBy(UserContext.getUserId());
        defect.setDisposeTime(LocalDateTime.now());
        defect.setStatus(1);
        defectMapper.updateById(defect);
    }

    /** 质量异常登记/更新处理信息（id 为空则新建） */
    @Transactional(rollbackFor = Exception.class)
    public Long saveException(Long id, String title, Integer level, Long orderId,
                              String description, String causeAnalysis, String action, Integer status) {
        MesQcException exception;
        if (id == null) {
            exception = new MesQcException();
            exception.setCode(codeGenerator.next("QE"));
            exception.setReporterId(UserContext.getUserId());
            exception.setStatus(0);
        } else {
            exception = exceptionMapper.selectById(id);
            if (exception == null) {
                throw new ServiceException(1404, "异常单不存在");
            }
        }
        exception.setTitle(title);
        exception.setLevel(level);
        exception.setOrderId(orderId);
        exception.setDescription(description);
        if (causeAnalysis != null) {
            exception.setCauseAnalysis(causeAnalysis);
        }
        if (action != null) {
            exception.setAction(action);
        }
        if (status != null && exception.getStatus() != 2) {
            exception.setStatus(status);
        }
        if (id == null) {
            exceptionMapper.insert(exception);
        } else {
            exceptionMapper.updateById(exception);
        }
        return exception.getId();
    }

    /** 关闭异常单 */
    @Transactional(rollbackFor = Exception.class)
    public void closeException(Long id) {
        MesQcException exception = exceptionMapper.selectById(id);
        if (exception == null) {
            throw new ServiceException(1404, "异常单不存在");
        }
        if (exception.getStatus() == 2) {
            throw new ServiceException(1409, "异常单已关闭");
        }
        exception.setStatus(2);
        exception.setCloseBy(UserContext.getUserId());
        exception.setCloseTime(LocalDateTime.now());
        exceptionMapper.updateById(exception);
    }

    /** 检验方案明细项（保存入参） */
    public record SchemeItem(String itemName, String standard, String method) {
    }
}
