package io.jansyk13.echo;

import io.jansyk13.Server;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;

public class HttpAggregatingEchoServer implements Server {
    private final ServerBootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ChannelFuture channelFuture;

    public HttpAggregatingEchoServer() throws InterruptedException {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-server-http-aggr-echo"));
        this.bootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(EpollServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(512 * 1024));

                        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpMessage>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpMessage msg) throws Exception {
                                ByteBuf requestContentCopy = msg.content().copy();
                                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, requestContentCopy);
                                response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, requestContentCopy.readableBytes());
                                ctx.write(response);
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.flush();
                                super.channelReadComplete(ctx);
                            }
                        });
                    }
                });

        this.channelFuture = this.bootstrap.bind(7777).sync();
    }

    public void close() throws InterruptedException {
        this.channelFuture.channel().close().sync();
        this.eventLoopGroup.shutdownGracefully();
    }
}
