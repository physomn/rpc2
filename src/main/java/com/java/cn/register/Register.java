package com.java.cn.register;

import com.java.cn.protocol.InvokeProtocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.util.concurrent.EventExecutorGroup;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Register {

    private List<String> serviceNames = new ArrayList<String>();
    private Map<String, Object> serviceMap = new ConcurrentHashMap<String, Object>();
    private int port;

    public Register(int port) {
        this.port = port;
        scannerClass("com.java.cn.provider");
        doRegister();
    }

    private void doRegister() {
        if (serviceNames.size() == 0)
            return;
        for (String className : serviceNames) {
            try {
                Class<?> clazz = Class.forName(className);
                String i = clazz.getInterfaces()[0].getName();
                serviceMap.put(i, clazz.newInstance());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 递归扫描指定包下的class
     *
     * @param packageName 指定报名
     */
    private void scannerClass(String packageName) {
        String path = this.getClass().getClassLoader().getResource(packageName.replaceAll("\\.", "/")).getPath();
        File root = new File(path);
        for (File file : root.listFiles()) {
            if (file.isDirectory()) {
                scannerClass(packageName + "." + file.getName());
            } else {
                serviceNames.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }

    private void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap server = new ServerBootstrap();
            server.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    .addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                                    .addLast(new LengthFieldPrepender(4))
                                    .addLast("encoder", new ObjectEncoder())
                                    .addLast("decoder", new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)))
                                    .addLast(new RegisterHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture future = server.bind(port).sync();
            System.out.println("服务器已启动，监听端口：" + port);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        Register r = new Register(8080);
        System.out.println(r.serviceNames);
        System.out.println(r.serviceMap);
        r.start();
    }

    private class RegisterHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            InvokeProtocol protocol = (InvokeProtocol) msg;
            Object result;
            if (serviceMap.containsKey(protocol.getClassName())) {
                Object obj = serviceMap.get(protocol.getClassName());
                String methodName = protocol.getMethodName();
                Method method = obj.getClass().getMethod(methodName, protocol.getParams());
                result = method.invoke(obj, protocol.getValues());
            } else {
                result = "404, Not Found!";
            }

            ctx.writeAndFlush(result);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
        }
    }
}
