package io.jansyk13.client.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.function.Consumer;

public class ReleaseHandler extends SimpleChannelInboundHandler {

    private final Consumer<Channel> releaser;

    public ReleaseHandler(Consumer<Channel> releaser) {
        this.releaser = releaser;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        releaser.accept(ctx.channel());
        if (evt instanceof ShouldCloseEvent) {
        }
        ctx.pipeline().remove(this);
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        releaser.accept(ctx.channel());
        ctx.close();
        ctx.pipeline().remove(this);
        super.exceptionCaught(ctx, cause);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // noop
    }

    public static class ShouldCloseEvent {

    }
}
