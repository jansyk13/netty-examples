package io.jansyk13.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class SocketClient implements Closeable {

    private static final String NETTY_CLIENT_SOCKET = "netty-client-socket";
    private final EpollEventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ChannelFuture channelFuture;

    public SocketClient() {
        this(7777);
    }

    public SocketClient(int port) {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory(NETTY_CLIENT_SOCKET));
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                    }
                });
        try {
            this.channelFuture = bootstrap.connect("localhost", port).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Promise<String> write(String content) {
        Channel channel = this.channelFuture.channel();
        final AtomicReference<String> response = new AtomicReference<>();

        DefaultPromise<String> promise = new DefaultPromise<>(channel.eventLoop());

        channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                response.getAndUpdate(current -> current == null
                        ? msg.toString(CharsetUtil.UTF_8)
                        : current + msg.toString(CharsetUtil.UTF_8));
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (!promise.isDone()) {
                    promise.setSuccess(response.get());
                }
                ctx.pipeline().remove(this);
                super.channelReadComplete(ctx);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                promise.setFailure(cause);
                ctx.pipeline().remove(this);
                super.exceptionCaught(ctx, cause);
            }
        });
        channel.writeAndFlush(Unpooled.copiedBuffer(content, CharsetUtil.UTF_8))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        return promise;
    }

    @Override
    public void close() throws IOException {
        this.channelFuture.channel().closeFuture();
        this.eventLoopGroup.shutdownGracefully();
    }
}
