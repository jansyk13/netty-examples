package io.jansyk13.pingpong;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.io.Closeable;
import java.io.IOException;

public class SocketPingPongServer implements Closeable {

    private final ServerBootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ChannelFuture channelFuture;

    public SocketPingPongServer() {
        this(7777);
    }

    public SocketPingPongServer(int port) {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-server-tcp-pingpong"));
        this.bootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(EpollServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                String string = msg.toString(CharsetUtil.UTF_8);
                                if (string.equalsIgnoreCase("ping")) {
                                    ctx.writeAndFlush(Unpooled.copiedBuffer("pong", CharsetUtil.UTF_8));
                                } else {
                                    ctx.writeAndFlush(Unpooled.copiedBuffer(String.format("Not ping - %s", string), CharsetUtil.UTF_8));
                                }
                            }
                        });
                    }
                });

        try {
            this.channelFuture = this.bootstrap.bind(port).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.channelFuture.channel().close().sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.eventLoopGroup.shutdownGracefully();

    }
}
