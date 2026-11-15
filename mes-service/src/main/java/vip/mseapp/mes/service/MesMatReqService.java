package vip.mseapp.mes.service;

import vip.mseapp.mes.entity.MesMatReq;
import vip.mseapp.mes.mapper.MesMatReqMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领退料业务：申请（领料/退料）→ 仓管发料/收料（线边仓台账联动）。
 */
@Service
public class MesMatReqService {

    /** 类型：1 领料 2 退料；状态：0 已申请 1 已执行 2 已驳回 */
    public static final int TYPE_ISSUE = 1, TYPE_RETURN = 2;

    private final MesMatReqMapper matReqMapper;
    private final MesStockService stockService;
    private final MesCodeGenerator codeGenerator;

    public MesMatReqService(MesMatReqMapper matReqMapper, MesStockService stockService,
                            MesCodeGenerator codeGenerator) {
        this.matReqMapper = matReqMapper;
        this.stockService = stockService;
        this.codeGenerator = codeGenerator;
    }

    /** 领料申请 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long orderId, Long materialId, BigDecimal qty, String remark) {
        return insert(TYPE_ISSUE, orderId, materialId, null, qty, remark);
    }

    /** 退料申请（批次必填） */
    @Transactional(rollbackFor = Exception.class)
    public Long returnReq(Long orderId, Long materialId, String batchNo, BigDecimal qty, String remark) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new ServiceException(1401, "退料必须指定批次");
        }
        return insert(TYPE_RETURN, orderId, materialId, batchNo, qty, remark);
    }

    private Long insert(int type, Long orderId, Long materialId, String batchNo, BigDecimal qty, String remark) {
        if (materialId == null || qty == null || qty.signum() <= 0) {
            throw new ServiceException(1401, "物料与数量必填且数量大于 0");
        }
        MesMatReq req = new MesMatReq();
        req.setCode(codeGenerator.next("MRQ"));
        req.setType(type);
        req.setOrderId(orderId);
        req.setMaterialId(materialId);
        req.setBatchNo(batchNo);
        req.setReqQty(qty);
        req.setApplicantId(UserContext.getUserId());
        req.setReqTime(LocalDateTime.now());
        req.setStatus(0);
        req.setRemark(remark);
        matReqMapper.insert(req);
        return req.getId();
    }

    /** 发料/收料执行：领料扣线边仓、退料入线边仓，同事务写台账流水 */
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long id) {
        MesMatReq req = matReqMapper.selectById(id);
        if (req == null) {
            throw new ServiceException(1404, "领退料单不存在");
        }
        if (req.getStatus() != 0) {
            throw new ServiceException(1409, "该单已处理");
        }
        boolean isIssue = req.getType() == TYPE_ISSUE;
        stockService.change(req.getMaterialId(), req.getBatchNo(),
                isIssue ? req.getReqQty().negate() : req.getReqQty(),
                isIssue ? MesStockService.REF_ISSUE : MesStockService.REF_RETURN,
                req.getId(), (isIssue ? "领料出库 " : "退料入库 ") + req.getCode());
        req.setStatus(1);
        req.setIssueBy(UserContext.getUserId());
        req.setIssueTime(LocalDateTime.now());
        matReqMapper.updateById(req);
    }

    /** 驳回申请 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        MesMatReq req = matReqMapper.selectById(id);
        if (req == null) {
            throw new ServiceException(1404, "领退料单不存在");
        }
        if (req.getStatus() != 0) {
            throw new ServiceException(1409, "该单已处理");
        }
        req.setStatus(2);
        matReqMapper.updateById(req);
    }
}
