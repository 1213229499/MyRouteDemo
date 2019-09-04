package com.ck.router_core;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.ck.router_annotation.model.RouteMeta;
import com.ck.router_core.callback.NavigationCallback;
import com.ck.router_core.exception.NoRouteFoundException;
import com.ck.router_core.template.IRouteGroup;
import com.ck.router_core.template.IRouteRoot;
import com.ck.router_core.template.IService;
import com.ck.router_core.utils.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

public class DNRouter {

    private static final String TAG = "DNRouter";
    private static final String ROUTE_ROOT_PACKAGE = "com.ck.router_routes";
    private static final String SDK_NAME = "DNRouter";
    private static final String SEPARATOR = "$$";
    private static final String SUFFIX_ROOT = "Root";
    private static Application mContext;

    private static DNRouter instance;

    public static DNRouter getInstance() {
        synchronized (DNRouter.class) {
            if (instance == null) {
                instance = new DNRouter();
            }
        }
        return instance;
    }

    public static void init(Application application) {
        mContext = application;
        try {
            loadInfo();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "DNRouter init: " + "初始化失败。。" + e);
        }
    }

    private static void loadInfo() throws
            PackageManager.NameNotFoundException, InterruptedException,
            ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        Set<String> routerMap = ClassUtils.getFileNamePackageName(mContext, ROUTE_ROOT_PACKAGE);

        for (String className : routerMap) {
            if (className.startsWith(ROUTE_ROOT_PACKAGE+ "." + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                ((IRouteRoot)Class.forName(className).getConstructor().newInstance()).loadInto(WareHouse.groupsIndex);
            }
        }

        for (Map.Entry<String, Class<? extends IRouteGroup>> stringClassEntry : WareHouse.groupsIndex.entrySet()) {
            Log.e(TAG, "Root映射表[ " + stringClassEntry.getKey() + " : " + stringClassEntry
                    .getValue() + "]");
        }
    }

    public Postcard build(String path) {
        if (TextUtils.isEmpty(path)) {
            throw new RuntimeException("路由地址无效！");
        } else {
            return build(path, extraGroup(path));
        }
    }

    public Postcard build(String path, String group) {
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(group)) {
            throw new RuntimeException("路由地址无效!");
        } else {
            return new Postcard(path, group);
        }
    }

    private String extraGroup(String path) {
        if (TextUtils.isEmpty(path) || !path.startsWith("/")) {
            throw new RuntimeException(path + " : 不能提取group.");
        }
        String defaultGroup = path.substring(1, path.indexOf("/", 1));
        if (TextUtils.isEmpty(defaultGroup)) {
            throw new RuntimeException(path + " : 不能提取group.");
        } else {
            return defaultGroup;
        }
    }

    protected Object navigation(Context context, final Postcard postcard, final int requestCode,
                                final NavigationCallback callback) {

        try {
            prepareCard(postcard);
        } catch (NoRouteFoundException e) {
            e.printStackTrace();
            if (null != callback) {
                callback.onLost(postcard);
            }
            return null;
        }
        if (null != callback) {
            callback.onFound(postcard);
        }

        switch (postcard.getType()) {
            case ACTIVITY:
                final Context currentContext = null == context ? mContext : context;
                final Intent intent = new Intent(currentContext, postcard.getDestination());
                intent.putExtras(postcard.getExtras());
                int flags = postcard.getFlags();
                if (-1 != flags) {
                    intent.setFlags(flags);
                } else if (!(currentContext instanceof Activity)) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (requestCode > 0) {
                            ActivityCompat.startActivityForResult((Activity)currentContext, intent, requestCode, postcard.getOptionsBundle());
                        } else {
                            ActivityCompat.startActivity(currentContext, intent, postcard.getOptionsBundle());
                        }

                        if ((0 != postcard.getEnterAnim() || 0 != postcard.getExtiAnim()) && currentContext instanceof Activity) {
                            ((Activity)currentContext).overridePendingTransition(postcard.getEnterAnim(), postcard.getExtiAnim());
                        }

                        if (null != callback) {
                            callback.onArrival(postcard);
                        }
                    }
                });
                break;
            case ISERVICE:
                return postcard.getService();
                default:
                    break;
        }
        return null;

    }

    private void prepareCard(Postcard postcard) {
        RouteMeta routeMeta = WareHouse.routes.get(postcard.getPath());
        if (null == routeMeta) {
            Class<? extends IRouteGroup> groupMeta = WareHouse.groupsIndex.get(postcard.getGroup());
            if (null == groupMeta) {
                throw  new NoRouteFoundException("没找到对应路由：" + postcard.getGroup() + " " + postcard.getPath());
            }
            IRouteGroup iGroupInstance;
            try {
                iGroupInstance = groupMeta.getConstructor().newInstance();

            } catch (Exception e) {
                throw new RuntimeException("路由分组映射表记录失败：" + e);
            }

            iGroupInstance.loadInto(WareHouse.routes);
            WareHouse.groupsIndex.remove(postcard.getGroup());
            prepareCard(postcard);
        } else {
            postcard.setDestination(routeMeta.getDestination());
            postcard.setType(routeMeta.getType());
            switch (routeMeta.getType()) {
                case ISERVICE:
                    Class<?> destination = routeMeta.getDestination();
                    IService service = WareHouse.services.get(destination);
                    if (null == service) {
                        try {
                            service = (IService) destination.getConstructor().newInstance();
                            WareHouse.services.put(destination, service);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    postcard.setService(service);
                    break;
                    default:break;
            }
        }
    }

    public void inject(Activity instance) {
        ExtraManager.getInstance().loadExtras(instance);
    }
}


























