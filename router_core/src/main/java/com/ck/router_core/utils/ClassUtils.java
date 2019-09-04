package com.ck.router_core.utils;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.ck.router_core.thread.DefaultPoolExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import dalvik.system.DexFile;

public class ClassUtils {

    public static List<String> getSourcePath(Context context) throws PackageManager.NameNotFoundException {

        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);

        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add(applicationInfo.sourceDir);

        if (null != applicationInfo.splitSourceDirs) {
            sourcePaths.addAll(Arrays.asList(applicationInfo.splitSourceDirs));
        }
        return sourcePaths;
    }

    public static Set<String> getFileNamePackageName(Application context, final String packageName)
            throws PackageManager.NameNotFoundException, InterruptedException {

        final Set<String> classNames = new HashSet<>();
        List<String> paths = getSourcePath(context);
        //使用同步计数器判断均处理完成
        final CountDownLatch parseCtl = new CountDownLatch(paths.size());

        ThreadPoolExecutor threadPoolExecutor = DefaultPoolExecutor.newDefaultPoolExecutor(paths.size());

        for (final String path : paths) {
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    DexFile dexFile = null;
                    try {
                        //加载apk中的dex 并遍历获得所有包名为{packageName}的类
                        dexFile = new DexFile(path);
                        Enumeration<String> dexEntries = dexFile.entries();
                        while (dexEntries.hasMoreElements()) {
                            String className = dexEntries.nextElement();
                            if (className.startsWith(packageName)) {
                                classNames.add(className);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (null != dexFile) {
                            try {
                                dexFile.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        //释放一个
                        parseCtl.countDown();
                    }
                }
            });
        }
        //等待执行完成
        parseCtl.await();
        return classNames;

    }
}
