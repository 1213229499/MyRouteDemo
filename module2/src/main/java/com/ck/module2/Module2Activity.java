package com.ck.module2;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.ck.base.TestService;
import com.ck.router_annotation.Extra;
import com.ck.router_annotation.Route;
import com.ck.router_core.DNRouter;

@Route(path = "/module2/test")
public class Module2Activity extends Activity {

    @Extra
    String msg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module2);
        DNRouter.getInstance().inject(this);
        Log.d("wsj", "onCreate: 我是模块2 " + msg);

        if (!BuildConfig.isModule) {
            //组件
        } else {
            //集成模式
            TestService testService = (TestService) DNRouter.getInstance().build("/main/service1")
                    .navigation();
            testService.test();

            TestService testService1 = (TestService) DNRouter.getInstance().build("/main/service2")
                    .navigation();
            testService1.test();

            TestService testService2 = (TestService) DNRouter.getInstance().build("/module1/service")
                    .navigation();
            testService2.test();

            TestService testService3 = (TestService) DNRouter.getInstance().build("/module2/service")
                    .navigation();
            testService3.test();
        }
    }

    public void mainJump(View view) {
        if (BuildConfig.isModule){
            DNRouter.getInstance().build("/main/test").withString("a",
                    "从Module2").navigation(this);
        }else{
            Toast.makeText(this,"当前处于组件模式,无法使用此功能",0).show();
        }
    }

    public void module1Jump(View view) {
        DNRouter.getInstance().build("/module1/test").withString("msg",
                "从Module2").navigation(this);
    }
}
