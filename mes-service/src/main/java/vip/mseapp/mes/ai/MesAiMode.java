package vip.mseapp.mes.ai;

/**
 * MES AI 能力模式（基座「参数设置」sys_config 参数 {@link #CONFIG_KEY} 控制，两端独立：本租户覆盖行优先，
 * 回退本库默认模板行）：
 * off 关闭 / auto 优先 AI 服务、失败回退直连 / platform 仅 AI 服务 / direct 仅直连模型（不依赖 mse-ai）。
 *
 * <p>对齐 mse-oa 的 {@code OaAiMode} 范式（规划 docs/01 第十三节）。
 */
public enum MesAiMode {

    OFF, AUTO, PLATFORM, DIRECT;

    /** sys_config 参数键（各库默认模板行 id=53200，租户可同名覆盖） */
    public static final String CONFIG_KEY = "mes.ai.mode";

    public boolean enabled() {
        return this != OFF;
    }

    public String value() {
        return name().toLowerCase();
    }

    /** 异常/空白值一律按 off 处理（fail-safe，不误开 AI 能力） */
    public static MesAiMode of(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        try {
            return MesAiMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }
}
