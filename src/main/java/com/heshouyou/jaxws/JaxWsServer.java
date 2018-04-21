package com.heshouyou.jaxws;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Netty 服务器
 * @Author heshouyou
 * @Date Create in 2018/4/1 13:37
 **/
public class JaxWsServer {

    public JaxWsServer(String ip, int port, Map<String,Object> mappings) {
        startNettyServer(ip, port ,mappings);
    }

    private void startNettyServer(String ip, int port, Map<String,Object> mappings) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup(500, Executors.newCachedThreadPool());
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.option(ChannelOption.SO_SNDBUF, 1048576);
            b.option(ChannelOption.SO_RCVBUF, 1048576);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpRequestDecoder());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new HttpResponseEncoder());
                            p.addLast(new ChunkedWriteHandler());
                            p.addLast(new JaxWsHandler(mappings));
                        }
                    });
            ChannelFuture f = b.bind(ip,port).sync();
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
