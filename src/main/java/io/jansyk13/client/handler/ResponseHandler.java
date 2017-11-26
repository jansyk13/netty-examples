package io.jansyk13.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Subscriber;

public class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final Subscriber<HttpContent> subscriber;
    private final Promise<HttpResponse> promise;

    public ResponseHandler(Subscriber<HttpContent> subscriber, Promise<HttpResponse> promise) {
        this.subscriber = subscriber;
        this.promise = promise;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse) {
            promise.setSuccess((HttpResponse) msg);
        } else if (msg instanceof LastHttpContent) {
            subscriber.onNext((HttpContent) msg);
            subscriber.onComplete();
            ctx.pipeline().remove(this);
        } else if (msg instanceof HttpContent) {
            subscriber.onNext((HttpContent) msg);
        } else {
            RuntimeException cause = new RuntimeException(String.format("Unexpected type of message - %s", msg.getClass().getSimpleName()));
            if (promise.isDone()) {
                subscriber.onError(cause);
            }
            subscriber.onError(cause);
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        promise.setFailure(cause);
        subscriber.onError(cause);
        ctx.pipeline().remove(this);
        super.exceptionCaught(ctx, cause);
    }
}
