package com.ck.router_compile.processor;

import com.ck.router_annotation.Route;
import com.ck.router_annotation.model.RouteMeta;
import com.ck.router_compile.utils.Consts;
import com.ck.router_compile.utils.Log;
import com.ck.router_compile.utils.Utils;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.lang.model.element.Modifier.PUBLIC;

@AutoService(Processor.class)
/**
 * 处理器接收的参数 替代 {@link AbstractProcessor#getSupportedOptions()} 函数
 */
@SupportedOptions(Consts.ARGUMENTS_NAME)
/**
 * 指定使用的Java版本 替代 {@link AbstractProcessor#getSupportedSourceVersion()} 函数
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
/**
 * 注册给哪些注解的  替代 {@link AbstractProcessor#getSupportedAnnotationTypes()} 函数
 */
@SupportedAnnotationTypes({Consts.ANN_TYPE_ROUTE})
public class RouteProcessor extends AbstractProcessor {

    private Map<String, String> rootMap = new TreeMap<>();

    private Map<String, List<RouteMeta>> groupMap = new HashMap<>();

    /**
     * 文件生成器
     */
    private Filer filerUtils;

    /**
     * 节点工具类（类、函数、属性都是节点）
     */
    private Elements elementsUtils;

    /**
     * type(类信息)工具类
     */
    private Types typeUtils;

    /**
     * 参数
     */
    private String moduleName;

    private Log log;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        log = Log.newLog(processingEnvironment.getMessager());
        elementsUtils = processingEnv.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        filerUtils = processingEnv.getFiler();
        //参数是模块名，为了防止多模块/组件化开发是时候，生成相同的xx$$ROOT$$文件
        Map<String, String> options = processingEnv.getOptions();
        if (!Utils.isEmpty(options)) {
            moduleName = options.get(Consts.ARGUMENTS_NAME);
        }
        log.i("RouteProcessor Parmaters : " + moduleName);
        if (Utils.isEmpty(moduleName)) {
            throw new RuntimeException("Not set Processor Paramters ");
        }

    }

    /**
     * 相当于main函数，正式处理注解
     * @param set 使用了支持处理注解的节点集合
     * @param roundEnvironment 表示当前或是之前的运行环境，可以通过该对象查找找到的注释
     * @return true 表示后续处理器不会再处理（已经处理）
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        if (!Utils.isEmpty(set)) {

            //拿到被注解的节点集合
            //这里面都是拿到类节点，因为Route是注解到类上面的
            Set<? extends Element> routeElements = roundEnvironment.getElementsAnnotatedWith(Route.class);

            if (!Utils.isEmpty(routeElements)) {
                try {
                    processRoute(routeElements);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return true;
        }

        return false;
    }

    /**
     * 处理被注解的节点
     * 这里只支持Activity的路由
     * @param routeElements
     */
    private void processRoute(Set<? extends Element> routeElements) throws IOException {

        //拿到Activity这个类的节点信息
        TypeElement activity = elementsUtils.getTypeElement(Consts.ACTIVITY);

        TypeMirror type_Activity = activity.asType();

        TypeElement iService = elementsUtils.getTypeElement(Consts.ISERVICE);
        TypeMirror type_IService = iService.asType();


        for (Element element : routeElements) {
            RouteMeta routeMeta;
            //拿到类信息
            TypeMirror typeMirror = element.asType();
            log.i("Route class:" + typeMirror.toString());
            //拿到注解
            Route route = element.getAnnotation(Route.class);
            //只能在指定的类上面使用
            if (typeUtils.isSubtype(typeMirror, activity.getSuperclass())) {
                //是不是Activity类型,是就保存在RouteMeta对象里面
                routeMeta = new RouteMeta(RouteMeta.Type.ACTIVITY, route, element);
            } else if (typeUtils.isSubtype(typeMirror, type_IService)) {
                routeMeta = new RouteMeta(RouteMeta.Type.ISERVICE, route, element);
            } else {
                throw new RuntimeException("Just support Activity Route");
            }
            categories(routeMeta);
        }

        //GroupMap集合有值了
        //生成 $$Group$$ 记录分组表  <地址，RouteMeta路由信息（Class文件等信息）>
        TypeElement iRouteGroup = elementsUtils.getTypeElement(Consts.IROUTE_GROUP);
        TypeElement iRouteRoot = elementsUtils.getTypeElement(Consts.IROUTE_ROOT);
        generatedGroup(iRouteGroup);

        //生成 $$Root$$ 记录路由表 <分组，对应的Group类>
        generatedRoot(iRouteRoot, iRouteGroup);
    }

    private void generatedRoot(TypeElement iRouteRoot, TypeElement iRouteGroup) throws IOException {
        //类型 Map<String,Class<? extends IRouteGroup>> routes>
        //Wildcard 通配符
        ParameterizedTypeName routes = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ParameterizedTypeName.get(
                        ClassName.get(Class.class),
                        WildcardTypeName.subtypeOf(ClassName.get(iRouteGroup))
                )
        );

        //参数 Map<String,Class<? extends IRouteGroup>> routes> routes
        ParameterSpec rootParamSpec = ParameterSpec.builder(routes, "routes")
                .build();
        //函数 public void loadInfo(Map<String,Class<? extends IRouteGroup>> routes> routes)
        MethodSpec.Builder loadIntoMethodOfRootBuilder = MethodSpec.methodBuilder
                (Consts.METHOD_LOAD_INTO)
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(rootParamSpec);

        //函数体
        for (Map.Entry<String, String> entry : rootMap.entrySet()) {
            loadIntoMethodOfRootBuilder.addStatement("routes.put($S, $T.class)", entry
                    .getKey(), ClassName.get(Consts.PACKAGE_OF_GENERATE_FILE, entry.getValue
                    ()));
        }
        //生成 $Root$类
        String rootClassName = Consts.NAME_OF_ROOT + moduleName;
        JavaFile.builder(Consts.PACKAGE_OF_GENERATE_FILE,
                TypeSpec.classBuilder(rootClassName)
                        .addSuperinterface(ClassName.get(iRouteRoot))
                        .addModifiers(PUBLIC)
                        .addMethod(loadIntoMethodOfRootBuilder.build())
                        .build()
        ).build().writeTo(filerUtils);

        log.i("Generated RouteRoot: " + Consts.PACKAGE_OF_GENERATE_FILE + "." + rootClassName);
    }

    /**
     * 生成 $$Group$$ 分组表
     * @param iRouteGroup
     */
    private void generatedGroup(TypeElement iRouteGroup) {
        //创建参数类型
        //Map<String, RouteMeta>
        ParameterizedTypeName parameterizedType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(RouteMeta.class));

        //创建参数
        ParameterSpec atlas = ParameterSpec.builder(parameterizedType, "atlas").build();

        //遍历分组 每一个分组 创建一个 $$Group$$
        for (Map.Entry<String, List<RouteMeta>> entry : groupMap.entrySet()) {
            /**
             * 创建这个方法
             * @Override
             * public void loadInto(Map<String, RouteMeta>) {}
             */
            MethodSpec.Builder method = MethodSpec.methodBuilder(
                    "loadInto")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(atlas)
                    .returns(TypeName.VOID);

            //分组名 与 对应分组中的信息
            String groupName = entry.getKey();

            List<RouteMeta> groupData = entry.getValue();
            for (RouteMeta routeMeta : groupData) {
                //添加函数体
                //atlas.put("/main/test", RouteMeta.build(RouteMeta.Type.ACTIVITY,SecondActivity.class, "/main/test", "main));
                //$S = String字符串
                //$T = Class类
                //$L = 字面量 （你写什么就是什么）
                method.addStatement(
                        "atlas.put($S, $T.build($T.$L, $T.class, $S,$S))",
                        routeMeta.getPath(),
                        ClassName.get(RouteMeta.class),
                        ClassName.get(RouteMeta.Type.class),
                        routeMeta.getType(),
                        ClassName.get((TypeElement) routeMeta.getElement()),
                        routeMeta.getPath().toLowerCase(),
                        routeMeta.getGroup().toLowerCase());

            }

            //类名
            String grroupClassName = Consts.NAME_OF_GROUP + groupName;

            //创建类
            TypeSpec typeSpec = TypeSpec.classBuilder(grroupClassName)
                    .addSuperinterface(ClassName.get(iRouteGroup))
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(method.build())
                    .build();

            //生成java文件
            JavaFile javaFile = JavaFile.builder(Consts.PACKAGE_OF_GENERATE_FILE, typeSpec).build();

            try {
                javaFile.writeTo(filerUtils);
            } catch (IOException e) {
                e.printStackTrace();
            }

            rootMap.put(groupName, grroupClassName);


        }
    }

    /**
     * 检查是否配置 group， 如果没有配置，则从path截取出组名
     * @param routeMeta
     */
    private void categories(RouteMeta routeMeta) {
        if (routeVerify(routeMeta)) {
            log.i("Group Info, Group Name = " + routeMeta.getGroup() + ", Path = " +
                    routeMeta.getPath());
            List<RouteMeta> routeMetas = groupMap.get(routeMeta.getGroup());
            //如果未记录分组则创建
            if (Utils.isEmpty(routeMetas)) {
                List<RouteMeta> routeMetaSet = new ArrayList<>();
                routeMetaSet.add(routeMeta);
                groupMap.put(routeMeta.getGroup(), routeMetaSet);
            } else {
                routeMetas.add(routeMeta);
            }
        } else {
            log.i("Group Info Error: " + routeMeta.getPath());
        }
    }

    /**
     * 验证路由信息必须存在path(并且设置分组)
     *
     * @param meta raw meta
     */
    private boolean routeVerify(RouteMeta meta) {
        String path = meta.getPath();
        String group = meta.getGroup();
        //路由地址必须以 / 开头
        if (Utils.isEmpty(path) || !path.startsWith("/")) {
            return false;
        }
        //如果没有设置分组,以第一个 / 后的节点为分组(所以必须path两个/)
        if (Utils.isEmpty(group)) {
            String defaultGroup = path.substring(1, path.indexOf("/", 1));
            if (Utils.isEmpty(defaultGroup)) {
                return false;
            }
            meta.setGroup(defaultGroup);
            return true;
        }
        return true;
    }
}
