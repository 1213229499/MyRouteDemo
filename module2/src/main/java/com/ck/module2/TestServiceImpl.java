package com.ck.module2;

import android.util.Log;

import com.ck.base.TestService;
import com.ck.router_annotation.Route;


/**
 * @author Lance
 * @date 2018/3/6
 */

@Route(path = "/module2/service")
public class TestServiceImpl implements TestService {


    @Override
    public void test() {
        Log.i("Service", "我是Module2模块测试服务通信");
    }
}

