package io.jansyk13.echo;

import io.jansyk13.Server;
import io.netty.bootstrap.ServerBootstrap;
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
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

public class HttpEchoServer implements Server {
    private final ServerBootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ChannelFuture channelFuture;

    public HttpEchoServer() throws InterruptedException {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-server-http-echo"));
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

                        pipeline.addLast(new SimpleChannelInboundHandler<HttpObject>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                                if (msg instanceof HttpRequest) {
                                    DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                                    HttpHeaders headers = ((HttpRequest) msg).headers();
                                    if (headers != null) {
                                        Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);
                                        if (contentLength != null) {
                                            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, contentLength);
                                        } else {

                                            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0);
                                        }
                                    }
                                    ctx.write(response);
                                } else if (msg instanceof HttpContent) {
                                    ctx.write(((HttpContent) msg).retain());
                                } else {
                                    ctx.fireExceptionCaught(new RuntimeException(String.format("Invalid message type - %s", msg.getClass().getSimpleName())));
                                }
                            }

                            @Override
                            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                ctx.flush();
                                ctx.close();
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
