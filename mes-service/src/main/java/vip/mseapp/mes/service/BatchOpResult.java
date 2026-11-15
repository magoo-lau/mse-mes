package vip.mseapp.mes.service;

import java.util.List;

/**
 * 批量操作结果（导入/批量删除/批量流转共用）：部分成功语义，失败项原因逐条收集。
 *
 * @param successCount 成功条数
 * @param failCount    失败条数
 * @param errors       失败原因（导入为「第N行：…」，批量操作为「id=xxx：…」）
 */
public record BatchOpResult(int successCount, int failCount, List<String> errors) {
}
