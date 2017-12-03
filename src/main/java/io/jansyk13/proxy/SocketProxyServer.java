package io.jansyk13.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
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
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class SocketProxyServer implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(SocketProxyServer.class);

    private static final AttributeKey<Channel> downstreamChannelKey = AttributeKey.valueOf(Channel.class, "downstreamChannel");

    private final ServerBootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final ChannelFuture channelFuture;
    private final int proxyPort;
    private final int downstreamPort;

    public SocketProxyServer(int proxyPort, int downstreamPort) throws InterruptedException {
        this.proxyPort = proxyPort;
        this.downstreamPort = downstreamPort;
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory("netty-server-socket-proxy"));
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
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                ctx.channel().attr(downstreamChannelKey).set(createDownstreamChannel(ctx.channel()));
                                super.channelActive(ctx);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                Channel downstreamChannel = ctx.channel().attr(downstreamChannelKey).get();
                                logger.info("proxy-down content={}", msg.toString(CharsetUtil.UTF_8));
                                downstreamChannel.writeAndFlush(msg.retain());
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                ctx.channel().attr(downstreamChannelKey).getAndSet(null)
                                        .pipeline()
                                        .fireChannelInactive();
                                ctx.close();
                                super.channelInactive(ctx);
                            }
                        });
                    }
                });

        this.channelFuture = this.bootstrap.bind(this.proxyPort).sync();
    }

    private Channel createDownstreamChannel(Channel upstreamChannel) throws InterruptedException {
        Bootstrap clientBootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                logger.info("proxy-up content={}", msg.toString(CharsetUtil.UTF_8));
                                upstreamChannel.writeAndFlush(msg.retain());
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                ctx.close();
                                super.channelInactive(ctx);
                            }
                        });
                    }
                });

        return clientBootstrap.connect("localhost", this.downstreamPort).sync().channel();
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
