import request from './request'

// 所有雪花 id 在 JSON 里是字符串（全局 Long→String 序列化），移动端一律按字符串处理

export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

/** 工单（MesOrder） */
export interface MesOrder {
  id: string
  code: string
  productId: string
  planQty: number
  finishedQty: number
  okQty: number
  ngQty: number
  planStart: string | null
  planEnd: string | null
  /** 0 草稿 1 已提交 2 已下达 3 生产中 4 完工 5 已关闭 6 已取消 */
  status: number
  remark: string | null
}

/** 报工记录（MesReport） */
export interface MesReport {
  id: string
  code: string
  orderId: string
  dispatchId: string | null
  stepId: string | null
  workstationId: string | null
  /** 1 正常 2 返工 */
  type: number
  okQty: number
  ngQty: number
  defectReason: string | null
  reportTime: string | null
  /** 0 待审核 1 已审核 2 已驳回 */
  status: number
  auditRemark: string | null
  createTime: string
}

/** 点检计划（MesCheckPlan） */
export interface MesCheckPlan {
  id: string
  name: string
  equipmentId: string
  /** 1 点检 2 保养 */
  type: number
  /** 1 日 2 周 3 月 */
  cycle: number
  nextDate: string | null
  content: string | null
  status: number
}

/** 点检记录（MesCheckRecord） */
export interface MesCheckRecord {
  id: string
  planId: string | null
  equipmentId: string
  checkDate: string
  /** 1 正常 2 异常 */
  result: number
  checkTime: string | null
  remark: string | null
}

/** 设备（MesEquipment） */
export interface MesEquipment {
  id: string
  code: string
  name: string
  model: string | null
  status: number
  remark: string | null
}

// -------------------- 报工 --------------------

/** 在制工单（已下达/生产中），报工选择与扫码匹配数据源 */
export const listOrders = () =>
  request.get<never, MesOrder[]>('/mes/order/list', { params: { statuses: '2,3' } })

export const submitReport = (data: {
  orderId: string
  /** 1 正常 2 返工，缺省 1 */
  type?: number
  okQty: number
  ngQty: number
  defectReason?: string
}) => request.post<never, MesReport>('/mes/report', data)

export const pageReport = (params: { pageNum: number; pageSize: number }) =>
  request.get<never, PageResult<MesReport>>('/mes/report/page', { params })

// -------------------- 设备点检 --------------------

export const pageCheckPlans = (params: { pageNum: number; pageSize: number }) =>
  request.get<never, PageResult<MesCheckPlan>>('/mes/check/plan/page', { params })

export const submitCheck = (data: {
  planId?: string
  equipmentId: string
  /** 1 正常 2 异常（异常自动生成维修工单） */
  result: number
  remark?: string
}) => request.post('/mes/check/record', data)

export const pageCheckRecords = (params: { pageNum: number; pageSize: number }) =>
  request.get<never, PageResult<MesCheckRecord>>('/mes/check/record/page', { params })

// -------------------- 设备 --------------------

/** 设备状态总览：{ list: 全部设备, counts: { "1": 3, ... } } */
export const equipmentStatus = () =>
  request.get<never, { list: MesEquipment[]; counts: Record<string, number> }>('/mes/equipment/status')
