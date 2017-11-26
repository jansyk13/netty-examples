package io.jansyk13.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;
import java.io.IOException;

public class HttpAggregatingClient implements Closeable {

    private static final String NETTY_CLIENT_AGGR_HTTP = "netty-client-http-aggr";
    private final EpollEventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ChannelFuture channelFuture;

    public HttpAggregatingClient() {
        this(7777);
    }

    public HttpAggregatingClient(int port) {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory(NETTY_CLIENT_AGGR_HTTP));
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(4096));
                    }
                });
        try {
            this.channelFuture = bootstrap.connect("localhost", port).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Promise<FullHttpResponse> write(FullHttpRequest request) {
        Channel channel = this.channelFuture.channel();

        DefaultPromise<FullHttpResponse> promise = new DefaultPromise<>(channel.eventLoop());

        channel.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                promise.setSuccess(msg.copy());
                ctx.pipeline().remove(this);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                promise.setFailure(cause);
                ctx.pipeline().remove(this);
                super.exceptionCaught(ctx, cause);
            }
        });
        channel.writeAndFlush(request)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        return promise;
    }

    @Override
    public void close() throws IOException {
        this.channelFuture.channel().closeFuture();
        this.eventLoopGroup.shutdownGracefully();
    }
}
