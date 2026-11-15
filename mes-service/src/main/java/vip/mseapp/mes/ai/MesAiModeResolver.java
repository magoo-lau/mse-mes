package vip.mseapp.mes.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vip.mseapp.core.constant.MseConstants;
import vip.mseapp.core.context.TenantContext;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mes.ai.MesAiRpcConfiguration.MesAiConfigHolder;

import java.util.List;
import java.util.Map;

/**
 * MES AI 能力模式解析：读基座「参数设置」sys_config 参数 {@link MesAiMode#CONFIG_KEY}。
 *
 * <p>租户与平台两端完全独立，按当前会话所在世界单边读取：租户会话 = 本租户覆盖行 → 本库默认模板行
 * （tenant_id=0 同库副本）；平台会话 = 平台库自身参数行。不再跨库合并平台默认（对齐 {@code OaAiModeResolver}
 * 修正后的范式）。参数契约缺失（未接 service-system）或读取失败 fail-open 按 off 处理（不中断业务、不误开 AI）。
 * 无缓存，改即生效。
 */
@Service
public class MesAiModeResolver {

    private static final Logger log = LoggerFactory.getLogger(MesAiModeResolver.class);

    private final ObjectProvider<MesAiConfigHolder> configHolder;

    public MesAiModeResolver(ObjectProvider<MesAiConfigHolder> configHolder) {
        this.configHolder = configHolder;
    }

    /** 解析当前会话生效的能力模式 */
    public MesAiMode resolve() {
        try {
            MesAiConfigHolder holder = configHolder.getIfAvailable();
            if (holder == null || holder.config() == null) {
                return MesAiMode.OFF;
            }
            Long tenantId = TenantContext.getTenantId();
            Long effective = tenantId == null ? MseConstants.PLATFORM_TENANT_ID : tenantId;
            return MesAiMode.of(readQuietly(holder, effective).get(MesAiMode.CONFIG_KEY));
        } catch (Exception e) {
            log.warn("MES AI 模式参数解析失败，按 off 处理", e);
            return MesAiMode.OFF;
        }
    }

    /** off 时抛出可操作提示（全部 AI 入口统一守卫） */
    public void ensureEnabled() {
        if (resolve() == MesAiMode.OFF) {
            throw new ServiceException("AI 能力未开启：请管理员在「参数设置」配置参数 "
                    + MesAiMode.CONFIG_KEY + "（off/auto/platform/direct）");
        }
    }

    private Map<String, String> readQuietly(MesAiConfigHolder holder, Long tenantId) {
        try {
            Map<String, String> values = holder.config().getConfigValues(tenantId, List.of(MesAiMode.CONFIG_KEY));
            return values == null ? Map.of() : values;
        } catch (Exception e) {
            log.warn("系统参数读取失败：key={}, tenantId={}", MesAiMode.CONFIG_KEY, tenantId);
            return Map.of();
        }
    }
}
