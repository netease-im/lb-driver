package com.netease.nim.lbd.config.server.model;

/**
 * 客户端能力等级，与 lb-driver 侧约定一致，是单调递增的整数
 * <p>
 * 服务端只做整数比较（不做版本串比较），枚举只用于把等级映射成日志可读名
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public enum ClientFeatureLevel {

    /**
     * 历史版本：不具备多 az 兜底能力，请求未携带等级字段时按本级处理
     */
    LEGACY(1, "legacy"),

    /**
     * 支持 az 亲和集合，且具备集合整体不可用时的降级兜底能力
     */
    AZ_FALLBACK(2, "az-fallback"),
    ;

    /**
     * 下发可用区集合所需的最低能力等级，是协议常量，不提供配置开关
     */
    public static final int MIN_LEVEL_FOR_AZ = AZ_FALLBACK.level;

    private final int level;
    private final String displayName;

    ClientFeatureLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * levelOf
     * @param level level
     * @return ClientFeatureLevel，未定义的等级返回 null
     */
    private static ClientFeatureLevel levelOf(int level) {
        for (ClientFeatureLevel value : values()) {
            if (value.level == level) {
                return value;
            }
        }
        return null;
    }

    /**
     * displayNameOf
     * @param level level
     * @return 日志可读名，未定义的等级返回 unknown(N)
     */
    public static String displayNameOf(int level) {
        ClientFeatureLevel featureLevel = levelOf(level);
        return featureLevel != null ? featureLevel.displayName : "unknown(" + level + ")";
    }
}
