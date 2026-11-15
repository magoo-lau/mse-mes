import { createRequest, downloadFile } from 'mse-ui-shared'
import { elementPlusToast } from 'mse-ui-shared/pc'
import { useAuthStore } from '../stores/auth'

/** 请求实例收敛至 mse-ui-shared 工厂（code===0 解包、401 嵌入模式上报主应用登出、skipErrorToast 单请求静默） */
const request = createRequest({ getToken: () => useAuthStore().token, toast: elementPlusToast, rejectBody: true, networkErrorFromResponse: true })

export default request

/** 文件下载（Excel 等 blob 响应），语义同共享包 downloadFile */
export async function download(url: string, params?: Record<string, unknown>, defaultName = '下载文件') {
  return downloadFile(url, params, { getToken: () => useAuthStore().token, toast: elementPlusToast }, defaultName)
}
