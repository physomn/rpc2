package com.java.cn.consumer;

import com.java.cn.api.HelloService;
import com.java.cn.api.NumberService;
import com.java.cn.consumer.proxy.ConsumerProxy;

public class Consumer {

    public static void main(String[] args) {
        HelloService hello = ConsumerProxy.create(HelloService.class);
        System.out.println(hello.sayHello("psysomn"));
        NumberService number = ConsumerProxy.create(NumberService.class);
        System.out.println(number.add(4, 2));
        System.out.println(number.sub(4, 2));
        System.out.println(number.multi(4, 2));
        System.out.println(number.div(4, 2));
        System.out.println(123);
    }
}
