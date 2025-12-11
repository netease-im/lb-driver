package com.netease.nim.lbd.config.server.springboot;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class LbdConfigServerImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] { LbdConfigServerConfigurationStarter.class.getName() };
    }
}
