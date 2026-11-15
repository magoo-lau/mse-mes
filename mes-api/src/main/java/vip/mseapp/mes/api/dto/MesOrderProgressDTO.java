package vip.mseapp.mes.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工单生产进度 DTO（契约传输对象，Serializable）。
 */
public class MesOrderProgressDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工单号 */
    private String orderCode;

    /** 产品物料 id */
    private Long productId;

    /** 计划数量 */
    private BigDecimal planQty;

    /** 完工数量 */
    private BigDecimal finishedQty;

    /** 合格数量 */
    private BigDecimal okQty;

    /** 不良数量 */
    private BigDecimal ngQty;

    /** 状态：2 已下达 3 生产中 4 完工 5 已关闭（见规划文档 01） */
    private Integer status;

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getPlanQty() {
        return planQty;
    }

    public void setPlanQty(BigDecimal planQty) {
        this.planQty = planQty;
    }

    public BigDecimal getFinishedQty() {
        return finishedQty;
    }

    public void setFinishedQty(BigDecimal finishedQty) {
        this.finishedQty = finishedQty;
    }

    public BigDecimal getOkQty() {
        return okQty;
    }

    public void setOkQty(BigDecimal okQty) {
        this.okQty = okQty;
    }

    public BigDecimal getNgQty() {
        return ngQty;
    }

    public void setNgQty(BigDecimal ngQty) {
        this.ngQty = ngQty;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
