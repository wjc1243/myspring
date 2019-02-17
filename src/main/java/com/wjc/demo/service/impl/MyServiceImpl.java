package com.wjc.demo.service.impl;

import com.wjc.demo.service.IMyService;
import com.wjc.mvcframework.annotation.MyService;

@MyService("myService")
public class MyServiceImpl implements IMyService {

    @Override
    public String get(String name) {
        return "Hello " + name;
    }
}
