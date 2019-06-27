package com.java.cn.provider;

import com.java.cn.api.HelloService;

public class HelloServiceImpl implements HelloService {
    public String sayHello(String msg) {
        msg = "Hello, " + msg;
        System.out.println(msg);
        return msg;
    }
}
