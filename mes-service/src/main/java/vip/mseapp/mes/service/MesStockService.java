package vip.mseapp.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import vip.mseapp.mes.entity.MesBatch;
import vip.mseapp.mes.entity.MesStock;
import vip.mseapp.mes.entity.MesStockLog;
import vip.mseapp.mes.mapper.MesBatchMapper;
import vip.mseapp.mes.mapper.MesStockLogMapper;
import vip.mseapp.mes.mapper.MesStockMapper;
import vip.mseapp.core.context.UserContext;
import vip.mseapp.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边仓库存业务：物料×批次台账 + 流水。所有数量变动必须经本服务（台账与流水同事务写入）。
 */
@Service
public class MesStockService {

    /** 流水来源：1 领料出库 2 退料入库 3 报工产出 4 手工调整 */
    public static final int REF_ISSUE = 1, REF_RETURN = 2, REF_PRODUCE = 3, REF_ADJUST = 4;

    private final MesStockMapper stockMapper;
    private final MesStockLogMapper stockLogMapper;
    private final MesBatchMapper batchMapper;

    public MesStockService(MesStockMapper stockMapper, MesStockLogMapper stockLogMapper,
                           MesBatchMapper batchMapper) {
        this.stockMapper = stockMapper;
        this.stockLogMapper = stockLogMapper;
        this.batchMapper = batchMapper;
    }

    /** 变动库存（正=入，负=出；出库余额不足抛异常），并写流水。batchNo 为空时归入「无批次」档 */
    @Transactional(rollbackFor = Exception.class)
    public void change(Long materialId, String batchNo, BigDecimal changeQty, int refType, Long refId, String remark) {
        if (materialId == null || changeQty == null || changeQty.signum() == 0) {
            throw new ServiceException(1401, "物料与变动数量必填且不为 0");
        }
        String batch = batchNo == null || batchNo.isBlank() ? "-" : batchNo;
        MesStock stock = stockMapper.selectOne(new LambdaQueryWrapper<MesStock>()
                .eq(MesStock::getMaterialId, materialId)
                .eq(MesStock::getBatchNo, batch)
                .last("LIMIT 1"));
        BigDecimal balance;
        if (stock == null) {
            if (changeQty.signum() < 0) {
                throw new ServiceException(1409, "线边仓库存不足（无该批次台账）");
            }
            stock = new MesStock();
            stock.setMaterialId(materialId);
            stock.setBatchNo(batch);
            stock.setQty(changeQty);
            stock.setLocation("LINE");
            stockMapper.insert(stock);
            balance = changeQty;
        } else {
            balance = stock.getQty().add(changeQty);
            if (balance.signum() < 0) {
                throw new ServiceException(1409, "线边仓库存不足：批次 " + batch + " 当前 " + stock.getQty());
            }
            stock.setQty(balance);
            stockMapper.updateById(stock);
        }
        MesStockLog log = new MesStockLog();
        log.setMaterialId(materialId);
        log.setBatchNo(batch);
        log.setChangeQty(changeQty);
        log.setBalanceQty(balance);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setOperatorId(UserContext.getUserId());
        log.setOpTime(LocalDateTime.now());
        log.setRemark(remark);
        stockLogMapper.insert(log);
    }

    /** 生产批次累计（报工产出；批次不存在则生成，批次号传空自动生成） */
    @Transactional(rollbackFor = Exception.class)
    public String accumulateBatch(Long materialId, Long orderId, BigDecimal qty, String batchNo) {
        MesBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<MesBatch>()
                .eq(MesBatch::getMaterialId, materialId)
                .eq(batchNo != null && !batchNo.isBlank(), MesBatch::getBatchNo, batchNo)
                .eq(MesBatch::getSource, 1)
                .eq(orderId != null, MesBatch::getOrderId, orderId)
                .last("LIMIT 1"));
        if (batch == null) {
            batch = new MesBatch();
            batch.setBatchNo(batchNo == null || batchNo.isBlank() ? nextBatchNo() : batchNo);
            batch.setMaterialId(materialId);
            batch.setSource(1);
            batch.setOrderId(orderId);
            batch.setQty(qty);
            batchMapper.insert(batch);
        } else {
            batch.setQty(batch.getQty().add(qty));
            batchMapper.updateById(batch);
        }
        return batch.getBatchNo();
    }

    private String nextBatchNo() {
        return "B" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", System.nanoTime() % 9000 + 1000);
    }
}
