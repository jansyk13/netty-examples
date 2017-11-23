package io.jansyk13.pingpong;

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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TcpPingPong {

    private static final Logger logger = LoggerFactory.getLogger(TcpPingPong.class);

    public static void run() throws Exception {
        TcpPingPongServer tcpPingPongServer = new TcpPingPongServer();

        EpollEventLoopGroup eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-client-tcp-pingpong"));
        Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                ctx.writeAndFlush(Unpooled.copiedBuffer("ping", CharsetUtil.UTF_8));
                                super.channelActive(ctx);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                String string = msg.toString(CharsetUtil.UTF_8);
                                logger.info(string);
                                if (!string.equalsIgnoreCase("pong")) {
                                    throw new RuntimeException("Not pong!");
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
                throw new RuntimeException("Fail TcpPingPong");
            }
        });

        channelFuture.channel().closeFuture().sync();
        eventLoopGroup.shutdownGracefully();

        tcpPingPongServer.close();
    }
}