package com.ck.myroutedemo;

import android.util.Log;

import com.ck.base.TestService;
import com.ck.router_annotation.Route;


/**
 * @author Lance
 * @date 2018/3/6
 */

@Route(path = "/main/service2")
public class TestServiceImpl2 implements TestService {


    @Override
    public void test() {
        Log.i("Service", "我是app模块测试服务通信2");
    }
}
