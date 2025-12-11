package com.netease.nim.lbd.config.server.springboot;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LbdConfigServerImportSelector.class)
@EntityScan(basePackageClasses = LbdConfigServerImportSelector.class)
public @interface EnableLbdConfigServer {
}
