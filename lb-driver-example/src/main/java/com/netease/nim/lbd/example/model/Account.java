package com.netease.nim.lbd.example.model;

/**
 * Created by caojiajun on 2025/12/10
 */
public class Account {
    private Long uid;
    private String account;
    private Integer validflag;
    private Long createTime;
    private Long updateTime;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public Integer getValidflag() {
        return validflag;
    }

    public void setValidflag(Integer validflag) {
        this.validflag = validflag;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
