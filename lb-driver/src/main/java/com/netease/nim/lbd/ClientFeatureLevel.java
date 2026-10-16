package com.netease.nim.lbd;

/**
 * 客户端能力等级，是 driver 与 config-server 共同约定的单调递增整数
 * <p>
 * 等级本身不需要对外可读，服务端只做整数比较，避免版本串比较踩坑；枚举只负责把等级映射成日志可读名
 * <p>
 * Created by caojiajun on 2026/10/15
 */
public enum ClientFeatureLevel {

    /**
     * 历史版本，不具备多 az 兜底能力，请求未携带等级字段时按本级处理
     */
    LEGACY(1, "legacy"),

    /**
     * 支持 az 亲和集合，且具备集合整体不可用时的降级兜底回退能力
     */
    AZ_FALLBACK(2, "az-fallback"),
    ;

    /**
     * 当前版本实现的能力等级，新增能力时追加枚举并更新这里
     */
    public static final ClientFeatureLevel CURRENT = AZ_FALLBACK;

    private final int level;
    private final String displayName;

    ClientFeatureLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    /**
     * getLevel
     * @return level
     */
    public int getLevel() {
        return level;
    }

    /**
     * getDisplayName
     * @return 日志可读名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * levelOf
     * @param level level
     * @return ClientFeatureLevel，未定义的等级返回 null
     */
    public static ClientFeatureLevel levelOf(int level) {
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
