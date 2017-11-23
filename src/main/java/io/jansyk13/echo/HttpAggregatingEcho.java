package io.jansyk13.echo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpAggregatingEcho {

    private static final Logger logger = LoggerFactory.getLogger(HttpAggregatingEcho.class);

    public static void run() throws Exception {
        HttpAggregatingEchoServer httpAggregatingEchoServer = new HttpAggregatingEchoServer();

        EpollEventLoopGroup eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-client-http-aggr-echo"));
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(4096));

                        pipeline.addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", Unpooled.copiedBuffer("echo", CharsetUtil.UTF_8));
                                request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
                                ctx.writeAndFlush(request);
                                super.channelActive(ctx);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                                String string = msg.content().toString(CharsetUtil.UTF_8);
                                logger.info(string);
                                if (!string.equalsIgnoreCase("echo")) {
                                    throw new RuntimeException("Not echo!");
                                }
                                ctx.close();
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                logger.info("exceptionCaught", cause);
                                ctx.close();
                                super.exceptionCaught(ctx, cause);
                            }
                        });
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect("localhost", 7777).sync();

        channelFuture.addListener(future -> {
            if (!future.isSuccess()) {
                throw new RuntimeException("Fail HttpAggrEcho");
            }
        });

        channelFuture.channel().closeFuture().sync();
        eventLoopGroup.shutdownGracefully();

        httpAggregatingEchoServer.close();
    }
}
