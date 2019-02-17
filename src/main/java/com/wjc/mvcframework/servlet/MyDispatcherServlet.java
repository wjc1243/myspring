package com.wjc.mvcframework.servlet;

import com.wjc.mvcframework.annotation.MyAutowired;
import com.wjc.mvcframework.annotation.MyController;
import com.wjc.mvcframework.annotation.MyRequestMapping;
import com.wjc.mvcframework.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    //与web.xml中的param-name一致
    private static final String LOCATION = "contextConfigLocation";

    //保存所有配置信息
    private Properties p = new Properties();

    //保存所有被扫描到的相关的类名
    private List<String> classNames = new ArrayList<>();

    //IOC容器，保存所有初始化的bean
    private Map<String, Object> IOC = new HashMap<>();

    //保存所有的url和方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    public MyDispatcherServlet() {
        super();
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        //1、加载配置文件
        doLoadConfig(servletConfig.getInitParameter(LOCATION));

        //2、扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

        //3、初始化所有相关类的实例，并保存到IOC容器中
        doInstance();

        //4、依赖注入
        doAutowired();

        //5、构造handlerMapping
        initHandlerMapping();

        //6、等待请求，匹配url，定位方法，反射调用执行
        //调用doGet或者doPost方法

        //提示信息
        System.out.println("My MVCFrameWork is init");
    }

    private void initHandlerMapping() {
        if(IOC.isEmpty()){
            return;
        }
        for(Map.Entry<String, Object> entry: IOC.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){
                continue;
            }
            String baseUrl = "";
            //获取controller的url配置
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = myRequestMapping.value();
            }
            //获取method的url配置
            Method[] methods = clazz.getMethods();
            for(Method method: methods){
                //没有加requestMapping注解的直接忽略
                if(!method.isAnnotationPresent(MyRequestMapping.class)){
                    continue;
                }
                //映射url
                MyRequestMapping myRequestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = ("/" + baseUrl + "/" + myRequestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("mapped " + url + "," + method);
            }
        }
    }

    /**
     * 依赖注入
     */
    private void doAutowired() {
        if(IOC.isEmpty()){
            return;
        }
        for(Map.Entry<String, Object> entry: IOC.entrySet()){
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field: fields){
                if(!field.isAnnotationPresent(MyAutowired.class)){
                    continue;
                }
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getName().trim();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), IOC.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    /**
     * 初始化所有相关的类，并放入到IOC容器之中。
     * IOC容器的key默认是类名首字母小写，如果是自己设置类名，则优先使用自定义的。
     * 因此，要先写一个针对类名首字母处理的工具方法。
     */
    private void doInstance() {
        if(classNames.size() == 0){
            return;
        }
        try {
            for(String className: classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    //默认首字母小写作为bean
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    IOC.put(beanName, clazz.newInstance());
                    continue;
                }else if(clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    String beanName = myService.value();
                    //如果用户设置了名字，就用用户自己设置
                    if(!"".equals(beanName.trim())){
                        IOC.put(beanName, clazz.newInstance());
                    }
                    //如果自己没设，就按接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i: interfaces){
                        IOC.put(i.getName(), clazz.newInstance());
                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 首字母小写
     */
    private String lowerFirstCase(String s){
        char[] sc = s.toCharArray();
        sc[0] += 32;
        return String.valueOf(sc);
    }

    /**
     * 递归扫描出所有的Class文件
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        //将所有的包路径转换为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for(File file: dir.listFiles()){
            //如果是文件夹，继续递归
            if(file.isDirectory()){
                doScanner(scanPackage+"."+file.getName());
            }else{
                classNames.add(scanPackage+"."+file.getName().replace(".class", "").trim());
            }
        }
    }

    /**
     * 将文件读取到Properties对象中：
     * @param location
     */
    private void doLoadConfig(String location) {
        InputStream in = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(location);
            //读取配置文件
            p.load(in);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if(in != null){
                    in.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //开始匹配到对应的方法
            doDispatch(req, resp);
        }catch (Exception e){
            //如果匹配信息出现异常，将异常信息打印出去
            resp.getWriter().write("500 Exception, details:\r\n" + Arrays.toString(e.getStackTrace())
            .replaceAll("\\[|\\]", "").replaceAll(",\\s", "\r\n"));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(this.handlerMapping.isEmpty()){
            return;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }

        Map<String, String[]> params = req.getParameterMap();
        Method method = this.handlerMapping.get(url);
        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取请求的参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];

        //方法的参数列表
        for (int i = 0; i < parameterTypes.length; i++) {
            //根据参数名称做某些处理
            Class parameterType = parameterTypes[i];
            if(parameterType == HttpServletRequest.class){
                //参数类型已明确，这边强制转型
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){
                for(Map.Entry<String, String[]> param: parameterMap.entrySet()){
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i] = value;
                }
            }
        }

        try {
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            //利用反射机制来调用
            method.invoke(this.IOC.get(beanName), paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
