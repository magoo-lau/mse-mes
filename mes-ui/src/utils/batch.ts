import { ElMessage, ElMessageBox } from 'element-plus'

/** 后端 BatchOpResult（导入/批量删除/批量流转共用返回） */
export interface BatchOpResult {
  successCount: number
  failCount: number
  errors: string[]
}

/**
 * 展示批量操作/导入结果：全部成功 toast；部分失败弹窗逐条列出原因（纯文本，防注入）。
 */
export function showBatchResult(result: BatchOpResult, action = '操作') {
  if (!result || result.failCount === 0) {
    ElMessage.success(`${action}完成：成功 ${result?.successCount ?? 0} 条`)
    return
  }
  const lines = [`成功 ${result.successCount} 条，失败 ${result.failCount} 条：`, ...result.errors].join('\n')
  ElMessageBox.alert(lines, `${action}部分失败`, { type: 'warning', customStyle: { whiteSpace: 'pre-line' } })
}
