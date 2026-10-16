package com.netease.nim.lbd.test;

import com.netease.nim.lbd.ClientFeatureLevel;
import com.netease.nim.lbd.Constants;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by caojiajun on 2026/10/15
 */
public class ClientFeatureLevelTest {

    @Test
    public void testLevelMapping() {
        Assert.assertEquals(1, ClientFeatureLevel.LEGACY.getLevel());
        Assert.assertEquals("legacy", ClientFeatureLevel.LEGACY.getDisplayName());
        Assert.assertEquals(2, ClientFeatureLevel.AZ_FALLBACK.getLevel());
        Assert.assertEquals("az-fallback", ClientFeatureLevel.AZ_FALLBACK.getDisplayName());
    }

    @Test
    public void testLevelOf() {
        Assert.assertEquals(ClientFeatureLevel.LEGACY, ClientFeatureLevel.levelOf(1));
        Assert.assertEquals(ClientFeatureLevel.AZ_FALLBACK, ClientFeatureLevel.levelOf(2));
        //未知等级不抛异常，日志里按可读名打印
        Assert.assertNull(ClientFeatureLevel.levelOf(3));
        Assert.assertNull(ClientFeatureLevel.levelOf(0));
    }

    @Test
    public void testDisplayNameOf() {
        Assert.assertEquals("az-fallback", ClientFeatureLevel.displayNameOf(2));
        Assert.assertEquals("unknown(3)", ClientFeatureLevel.displayNameOf(3));
    }

    @Test
    public void testCurrentLevel() {
        Assert.assertEquals(ClientFeatureLevel.AZ_FALLBACK, ClientFeatureLevel.CURRENT);
        Assert.assertEquals(2, Constants.CLIENT_FEATURE_LEVEL);
    }
}
