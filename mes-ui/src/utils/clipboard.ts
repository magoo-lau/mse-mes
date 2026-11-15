/**
 * 剪贴板复制：clipboard API 优先，http 非 localhost 环境 navigator.clipboard 为 undefined，
 * 回退 execCommand + 隐藏 textarea（仓库统一写法，见 AGENTS.md）。
 */
export function copyText(text: string): Promise<void> {
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text)
  }
  return new Promise((resolve, reject) => {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy') ? resolve() : reject(new Error('copy failed'))
    } catch (e) {
      reject(e as Error)
    } finally {
      document.body.removeChild(textarea)
    }
  })
}
