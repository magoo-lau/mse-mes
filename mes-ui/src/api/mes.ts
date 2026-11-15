import request from './request'

// 统一列表参数
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  [key: string]: unknown
}

const get = (url: string, params?: object, config?: object) => request.get(url, { params, ...config })
const post = (url: string, data?: object, config?: object) => request.post(url, data, config)
const put = (url: string, data?: object, config?: object) => request.put(url, data, config)
const del = (url: string) => request.delete(url)

/** AI 长耗时模型调用（1~3 分钟），单请求放宽超时（axios 实例默认 15s 面向普通 CRUD） */
const AI_TIMEOUT = { timeout: 180000 }

// ==================== 批量操作（批量删除 / 批量流转 / 导入导出） ====================
// ids 数组统一走 POST body（部分成功语义，返回 { successCount, failCount, errors }）
export const deleteModelBatch = (ids: number[]) => post('/mes/model/batch-delete', ids)
export const deleteMaterialBatch = (ids: number[]) => post('/mes/material/batch-delete', ids)
export const deleteBomBatch = (ids: number[]) => post('/mes/bom/batch-delete', ids)
export const deleteRouteBatch = (ids: number[]) => post('/mes/route/batch-delete', ids)
export const deleteCalendarBatch = (ids: number[]) => post('/mes/calendar/batch-delete', ids)
export const deleteEquipmentBatch = (ids: number[]) => post('/mes/equipment/batch-delete', ids)
export const deleteQcSchemeBatch = (ids: number[]) => post('/mes/qc/scheme/batch-delete', ids)
export const deleteSpcItemBatch = (ids: number[]) => post('/mes/spc/item/batch-delete', ids)

export const batchSubmitOrder = (ids: number[]) => post('/mes/order/batch/submit', ids)
export const batchReleaseOrder = (ids: number[]) => post('/mes/order/batch/release', ids)
export const batchCancelOrder = (ids: number[]) => post('/mes/order/batch/cancel', ids)
export const batchCloseOrder = (ids: number[]) => post('/mes/order/batch/close', ids)
export const batchAuditReport = (data: object) => post('/mes/report/batch/audit', data)
export const batchHandleDefect = (data: object) => post('/mes/qc/defect/batch/handle', data)
export const batchCloseQcException = (ids: number[]) => post('/mes/qc/exception/batch/close', ids)
export const batchIssueMatReq = (ids: number[]) => post('/mes/matreq/batch/issue', ids)
export const batchRejectMatReq = (ids: number[]) => post('/mes/matreq/batch/reject', ids)
export const batchChangeCheckPlanStatus = (status: number, ids: number[]) =>
  post(`/mes/check/plan/batch/status/${status}`, ids)

// ==================== 基础数据建模 ====================
export const listModel = (params?: object) => get('/mes/model/list', params)
export const saveModel = (data: object) => post('/mes/model', data)
export const updateModel = (id: number, data: object) => put(`/mes/model/${id}`, data)
export const deleteModel = (id: number) => del(`/mes/model/${id}`)

export const pageMaterial = (params: PageQuery) => get('/mes/material/page', params)
export const listMaterial = () => get('/mes/material/list')
export const saveMaterial = (data: object) => post('/mes/material', data)
export const updateMaterial = (id: number, data: object) => put(`/mes/material/${id}`, data)
export const deleteMaterial = (id: number) => del(`/mes/material/${id}`)

export const pageBom = (params: PageQuery) => get('/mes/bom/page', params)
export const getBom = (id: number) => get(`/mes/bom/${id}`)
export const saveBom = (data: object) => post('/mes/bom', data)
export const deleteBom = (id: number) => del(`/mes/bom/${id}`)

export const pageRoute = (params: PageQuery) => get('/mes/route/page', params)
export const getRoute = (id: number) => get(`/mes/route/${id}`)
export const saveRoute = (data: object) => post('/mes/route', data)
export const deleteRoute = (id: number) => del(`/mes/route/${id}`)
export const changeRouteStatus = (id: number, status: number) => post(`/mes/route/${id}/status/${status}`)

export const listCalendar = (month: string, lineId?: number) => get('/mes/calendar/list', { month, lineId })
export const saveCalendar = (data: object[]) => post('/mes/calendar/batch', data)
export const deleteCalendar = (id: number) => del(`/mes/calendar/${id}`)

// ==================== 生产计划 ====================
export const pageOrder = (params: PageQuery) => get('/mes/order/page', params)
export const listOrder = (params?: object) => get('/mes/order/list', params)
export const getOrder = (id: number) => get(`/mes/order/${id}`)
export const createOrder = (data: object) => post('/mes/order', data)
export const updateOrder = (id: number, data: object) => put(`/mes/order/${id}`, data)
export const deleteOrder = (id: number) => del(`/mes/order/${id}`)
export const submitOrder = (id: number) => post(`/mes/order/${id}/submit`)
export const releaseOrder = (id: number) => post(`/mes/order/${id}/release`)
export const cancelOrder = (id: number) => post(`/mes/order/${id}/cancel`)
export const closeOrder = (id: number) => post(`/mes/order/${id}/close`)
export const deleteOrderBatch = (ids: number[]) => post('/mes/order/batch-delete', ids)

export const listPlan = (params?: object) => get('/mes/plan/list', params)
export const savePlan = (data: object) => post('/mes/plan', data)
export const updatePlan = (id: number, data: object) => put(`/mes/plan/${id}`, data)
export const deletePlan = (id: number) => del(`/mes/plan/${id}`)
export const publishPlan = (ids: number[]) => post('/mes/plan/publish', ids)

// ==================== 生产执行 ====================
export const pageDispatch = (params: PageQuery) => get('/mes/dispatch/page', params)
export const assignDispatch = (id: number, data: object) => post(`/mes/dispatch/${id}/assign`, data)
export const transferDispatch = (data: object) => post('/mes/dispatch/transfer', data)

export const progressList = (orderId?: number) => get('/mes/exec/progress', { orderId })
export const wipList = (lineId?: number) => get('/mes/exec/wip', { lineId })
export const haltList = () => get('/mes/exec/halt/list')
export const haltPage = (params: PageQuery) => get('/mes/exec/halt/page', params)
export const saveHalt = (data: object) => post('/mes/exec/halt', data)
export const recoverHalt = (id: number) => post(`/mes/exec/halt/${id}/recover`)

// ==================== 报工 ====================
export const submitReport = (data: object) => post('/mes/report', data)
export const pageReport = (params: PageQuery) => get('/mes/report/page', params)
export const auditReport = (id: number, data: object) => post(`/mes/report/${id}/audit`, data)
export const undoReport = (id: number) => post(`/mes/report/${id}/undo`)
export const doRework = (data: object) => post('/mes/report/rework', data)

// ==================== 质量管理 ====================
export const pageQcScheme = (params: PageQuery) => get('/mes/qc/scheme/page', params)
export const getQcScheme = (id: number) => get(`/mes/qc/scheme/${id}`)
export const saveQcScheme = (data: object) => post('/mes/qc/scheme', data)
export const deleteQcScheme = (id: number) => del(`/mes/qc/scheme/${id}`)

export const pageQcOrder = (params: PageQuery) => get('/mes/qc/order/page', params)
export const submitQcOrder = (id: number, data: object) => post(`/mes/qc/order/${id}/submit`, data)

export const pageDefect = (params: PageQuery) => get('/mes/qc/defect/page', params)
export const handleDefect = (id: number, data: object) => post(`/mes/qc/defect/${id}/handle`, data)

export const pageQcException = (params: PageQuery) => get('/mes/qc/exception/page', params)
export const saveQcException = (data: object) => post('/mes/qc/exception', data)
export const closeQcException = (id: number) => post(`/mes/qc/exception/${id}/close`)

// ==================== 设备管理 ====================
export const pageEquipment = (params: PageQuery) => get('/mes/equipment/page', params)
export const listEquipment = () => get('/mes/equipment/list')
export const saveEquipment = (data: object) => post('/mes/equipment', data)
export const updateEquipment = (id: number, data: object) => put(`/mes/equipment/${id}`, data)
export const deleteEquipment = (id: number) => del(`/mes/equipment/${id}`)
export const equipmentStatus = () => get('/mes/equipment/status')

export const pageCheckPlan = (params: PageQuery) => get('/mes/check/plan/page', params)
export const saveCheckPlan = (data: object) => post('/mes/check/plan', data)
export const changeCheckPlanStatus = (id: number, status: number) => put(`/mes/check/plan/${id}/status/${status}`)
export const pageCheckRecord = (params: PageQuery) => get('/mes/check/record/page', params)
export const submitCheck = (data: object) => post('/mes/check/record', data)

export const pageRepair = (params: PageQuery) => get('/mes/repair/page', params)
export const createRepair = (data: object) => post('/mes/repair', data)
export const takeRepair = (id: number) => post(`/mes/repair/${id}/take`, {})
export const finishRepair = (id: number, handleDesc: string) => post(`/mes/repair/${id}/finish`, { handleDesc })

// ==================== 物料管理 ====================
export const pageMatReq = (params: PageQuery) => get('/mes/matreq/page', params)
export const createMatReq = (data: object) => post('/mes/matreq/issue-request', data)
export const returnMatReq = (data: object) => post('/mes/matreq/return-request', data)
export const issueMatReq = (id: number) => post(`/mes/matreq/${id}/issue`)
export const rejectMatReq = (id: number) => post(`/mes/matreq/${id}/reject`)

export const pageStock = (params: PageQuery) => get('/mes/stock/page', params)
export const adjustStock = (data: object) => post('/mes/stock/adjust', data)
export const pageStockLog = (params: PageQuery) => get('/mes/stock/log/page', params)

export const printLabel = (data: object) => post('/mes/trace/label/print', data)

// ==================== 追溯 ====================
export const traceForward = (batchNo: string) => get('/mes/trace/forward', { batchNo })
export const traceBackward = (batchNo: string) => get('/mes/trace/backward', { batchNo })
export const exportTrace = (batchNo: string) => get('/mes/trace/export', { batchNo })
export const pageSn = (params: PageQuery) => get('/mes/trace/sn/page', params)
export const bindSn = (data: object) => post('/mes/trace/sn/bind', data)

// ==================== 看板报表 ====================
export const dashboardOverview = () => get('/mes/dashboard/overview')
export const statsProduction = (days: number) => get('/mes/stats/production', { days })
export const statsQuality = (days: number, top = 10) => get('/mes/stats/quality', { days, top })
export const statsEquipment = (days: number) => get('/mes/stats/equipment', { days })
export const exportStats = (type: string, days: number) => get('/mes/stats/export', { type, days })

// ==================== AI 智能化（二期） ====================
export const aiCapability = () => get('/mes/ai/capability')
export const aiDailyReport = (days = 1) => post(`/mes/ai/daily-report?days=${days}`, undefined, AI_TIMEOUT)
export const aiQualityDiag = (id: number) => post('/mes/ai/quality-diag', { id }, AI_TIMEOUT)
export const aiEquipDiag = (id: number) => post('/mes/ai/equip-diag', { id }, AI_TIMEOUT)
export const aiDataQa = (question: string) => post('/mes/ai/data-qa', { question }, AI_TIMEOUT)
export const aiScheduleAdvise = () => post('/mes/ai/schedule-advise', undefined, AI_TIMEOUT)
export const aiKbQa = (question: string) => post('/mes/ai/kb-qa', { question }, AI_TIMEOUT)
export const aiAgentOptions = () => get('/mes/ai/agent-options')
export const aiAgentMap = () => get('/mes/ai/agent-map')
export const saveAiAgentMap = (scene: string, agentCode: string) => post('/mes/ai/agent-map', { scene, agentCode })

// ==================== 系统集成（二期，ERP 入站同步） ====================
export const integrationConfig = () => get('/mes/integration/config')
export const saveIntegrationConfig = (data: object) => post('/mes/integration/config', data)
export const pageIntegrationLog = (params: PageQuery) => get('/mes/integration/log/page', params)

// ==================== 设备数采监控（三期） ====================
export const iotRealtime = () => get('/mes/iot/realtime')
export const iotOee = (days = 7) => get('/mes/iot/oee', { days })
export const iotCollect = (data: object) => post('/mes/iot/collect', data)
export const pageIotCollect = (params: PageQuery) => get('/mes/iot/collect/page', params)
export const iotConfigList = () => get('/mes/iot/config')
export const saveIotConfig = (data: object) => post('/mes/iot/config', data)

// ==================== 安灯管理（三期） ====================
export const andonCall = (data: object) => post('/mes/andon/call', data)
export const andonRespond = (id: number) => post(`/mes/andon/${id}/respond`)
export const andonClose = (id: number, handleResult?: string) => post(`/mes/andon/${id}/close`, { handleResult })
export const andonActive = () => get('/mes/andon/active')
export const pageAndon = (params: PageQuery) => get('/mes/andon/page', params)
export const andonStats = (days = 7) => get('/mes/andon/stats', { days })
export const andonEscalateRun = () => post('/mes/andon/escalate/run')

// ==================== SPC 分析（三期） ====================
export const pageSpcItem = (params: PageQuery) => get('/mes/spc/item/page', params)
export const listSpcItem = () => get('/mes/spc/item/list')
export const saveSpcItem = (data: object) => post('/mes/spc/item', data)
export const deleteSpcItem = (id: number) => del(`/mes/spc/item/${id}`)
export const addSpcSample = (data: object) => post('/mes/spc/sample', data)
export const spcChart = (itemId: number, days = 30) => get('/mes/spc/chart', { itemId, days })

// ==================== AI（三期场景） ====================
export const aiAndonSummary = (days = 7) => post(`/mes/ai/andon-summary?days=${days}`, undefined, AI_TIMEOUT)
export const aiSpcInterpret = (id: number, days = 30) => post(`/mes/ai/spc-interpret?days=${days}`, { id }, AI_TIMEOUT)
