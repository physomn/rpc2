package com.java.cn.consumer.proxy;

import com.java.cn.protocol.InvokeProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ConsumerProxy {

    public static <T> T create(Class<?> clazz) {
        MethodProxy proxy = new MethodProxy(clazz);
        Class<?>[] interfaces = clazz.isInterface() ? new Class[]{clazz} : clazz.getInterfaces();
        T t = (T) Proxy.newProxyInstance(clazz.getClassLoader(), interfaces, proxy);
        return t;
    }

    private static class MethodProxy implements InvocationHandler {
        Class<?> clazz;

        public MethodProxy(Class<?> clazz) {
            this.clazz = clazz;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Object.class.equals(proxy.getClass())) {
                return method.invoke(proxy, args);
            } else {
                return rpcInvoke(proxy, method, args);
            }
        }

        private Object rpcInvoke(Object proxy, Method method, Object[] args) {
            InvokeProtocol protocol = new InvokeProtocol();
            protocol.setClassName(method.getDeclaringClass().getName());
            protocol.setMethodName(method.getName());
            protocol.setParams(method.getParameterTypes());
            protocol.setValues(args);

            final ConsumerHandler handler = new ConsumerHandler();
            EventLoopGroup group = new NioEventLoopGroup();
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel channel) throws Exception {
                            channel.pipeline()
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("encoder", new ObjectEncoder())
                                    .addLast("decoder", new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)))
                                    .addLast("handler", handler);
                        }
                    });
            try {
                ChannelFuture future = b.connect("localhost", 8080).sync();
                future.channel().writeAndFlush(protocol).sync();
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
            return handler.getResponse();
        }
    }
}
