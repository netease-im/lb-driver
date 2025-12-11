package com.netease.nim.lbd.example.dao;

import com.netease.nim.lbd.example.model.Account;
import com.netease.nim.lbd.example.model.Uinfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Created by caojiajun on 2025/12/10
 */
public interface TestDao {

    @Insert("insert into test_account (`account`, `validflag`, `create_time`, `update_time`)" +
            " values (#{account}, #{validflag}, #{createTime}, #{updateTime});")
    @Options(useGeneratedKeys = true, keyProperty = "uid")
    int createAccount(Account account);

    @Insert("insert into test_uinfo (`uid`, `nick`, `email`, `ext`, `create_time`, `update_time`)" +
            " values (#{uid}, #{nick}, #{email}, #{ext}, #{createTime}, #{updateTime});")
    int createUinfo(Uinfo uinfo);

    @Update("update test_uinfo set `nick` = #{nick}, `email` = #{email}, `ext` = #{ext}, `update_time` = #{updateTime} where uid = #{uid}")
    int updateUinfo(Uinfo uinfo);

    @Delete("delete from test_account where uid = #{uid}")
    int deleteAccount(@Param("uid") Long uid);

    @Delete("delete from test_uinfo where uid = #{uid}")
    int deleteUinfo(@Param("uid") Long uid);

    @Select("select `uid`, `account`, `validflag`, `create_time` as `createTime`, `update_time` as `updateTime` from test_account where account = #{account}")
    Account selectByAccount(@Param("account") String account);

    @Select("select `uid`, `nick`, `email`, `ext`, `create_time` as `createTime`, `update_time` as `updateTime` from test_uinfo where uid = #{uid}")
    Uinfo selectUinfoByUid(@Param("uid") Long uid);

    @Select("select `uid`, `nick`, `email`, `ext`, `create_time` as `createTime`, `update_time` as `updateTime` from test_uinfo order by create_time desc limit 100")
    List<Uinfo> selectUinfos();

    @Select("select `uid`, `account`, `validflag`, `create_time` as `createTime`, `update_time` as `updateTime` from test_account order by create_time desc limit 100")
    List<Account> selectAccounts();

    @Delete("delete from test_uinfo")
    int deleteUinfoAll();

    @Delete("delete from test_account")
    int deleteAccountAll();
}
