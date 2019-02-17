package com.wjc.demo.controller;

import com.wjc.demo.service.IMyService;
import com.wjc.mvcframework.annotation.MyAutowired;
import com.wjc.mvcframework.annotation.MyController;
import com.wjc.mvcframework.annotation.MyRequestMapping;
import com.wjc.mvcframework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/test")
public class TestController {
    @MyAutowired
    private IMyService myService;

    @MyRequestMapping("/get")
    public void get(HttpServletResponse response, @MyRequestParam("name") String name){
        //String res = "Hello " + name;
        String res = myService.get(name);
        try {
            response.getWriter().write(res);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @MyRequestMapping("/add")
    public void add(){
    }
}
