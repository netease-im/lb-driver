package com.netease.nim.lbd.example.service;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.example.dao.TestDao;
import com.netease.nim.lbd.example.model.Account;
import com.netease.nim.lbd.example.model.Uinfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Created by caojiajun on 2025/12/10
 */
@Service
public class TestService {

    private static final Logger log = LoggerFactory.getLogger(TestService.class);
    @Autowired
    private TestDao dao;

    @Transactional(rollbackFor = {Exception.class})
    public void create(String acc, boolean error) {
        Account account2 = dao.selectByAccount(acc);
        if (account2 != null) {
            throw new RuntimeException("account exists");
        }

        long now = System.currentTimeMillis();
        Account account = new Account();
        account.setAccount(acc);
        account.setValidflag(1);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        int account1 = dao.createAccount(account);
        log.info("create account, account = {}, ret = {}", JSONObject.toJSONString(account), account1);

        if (error) {
            throw new RuntimeException("sql error");
        }

        Uinfo uinfo = new Uinfo();
        uinfo.setUid(account.getUid());
        uinfo.setNick("nick-" + System.currentTimeMillis());
        uinfo.setEmail("zhangsan@163.com");
        uinfo.setExt("ext-" + System.currentTimeMillis());
        uinfo.setCreateTime(now);
        uinfo.setUpdateTime(now);
        int uinfo1 = dao.createUinfo(uinfo);
        log.info("create uinfo, uinfo = {}, ret = {}", JSONObject.toJSONString(uinfo), uinfo1);
    }

    @Transactional(rollbackFor = {Exception.class})
    public void delete(String acc, boolean error) {
        Account account = dao.selectByAccount(acc);
        if (account == null) {
            return;
        }
        int delete1 = dao.deleteAccount(account.getUid());
        log.info("delete account, account = {}, delete1 = {}", JSONObject.toJSONString(account), delete1);
        if (error) {
            throw new RuntimeException("sql error");
        }
        int delete2 = dao.deleteUinfo(account.getUid());
        log.info("delete account, account = {}, delete2 = {}", JSONObject.toJSONString(account), delete2);
    }

    @Transactional(rollbackFor = {Exception.class})
    public void deleteAll() {
        dao.deleteAccountAll();
        dao.deleteUinfoAll();
    }

    public void updateNick(String account, String nick) {
        Account account1 = dao.selectByAccount(account);
        Uinfo uinfo = dao.selectUinfoByUid(account1.getUid());
        uinfo.setNick(nick);
        uinfo.setUpdateTime(System.currentTimeMillis());
        dao.updateUinfo(uinfo);
    }

    public List<Account> selectAccount() {
        return dao.selectAccounts();
    }

    public List<Uinfo> selectUinfo() {
        return dao.selectUinfos();
    }
}
