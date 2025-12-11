package com.netease.nim.lbd.example.controller;

import com.alibaba.fastjson2.JSONObject;
import com.netease.nim.lbd.example.model.Account;
import com.netease.nim.lbd.example.model.Uinfo;
import com.netease.nim.lbd.example.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/10
 */
@RestController
public class TestController {

    @Autowired
    private TestService testService;

    @RequestMapping("/create")
    public String create(@RequestParam("account") String acc,
                         @RequestParam(value = "error", required = false, defaultValue = "false") boolean error) {
        testService.create(acc, error);
        return "ok";
    }

    @RequestMapping("/delete")
    public String delete(@RequestParam("account") String acc,
                         @RequestParam(value = "error", required = false, defaultValue = "false") boolean error) {
        testService.delete(acc, error);
        return "ok";
    }

    @RequestMapping("/deleteAll")
    public String delete() {
        testService.deleteAll();
        return "ok";
    }

    @RequestMapping("/update")
    public String update(@RequestParam("account") String account, @RequestParam("nick") String nick) {
        testService.updateNick(account, nick);
        return "ok";
    }

    @RequestMapping("/query")
    public String query() {
        List<Account> accounts = testService.selectAccount();
        List<Uinfo> uinfos = testService.selectUinfo();
        JSONObject json = new JSONObject();
        json.put("accounts", accounts);
        json.put("uinfos", uinfos);
        return json.toString();
    }
}
