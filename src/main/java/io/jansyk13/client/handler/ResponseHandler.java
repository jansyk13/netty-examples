package io.jansyk13.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Promise;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;

import java.util.concurrent.atomic.AtomicReference;

public class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {

    //TODO field update
    private AtomicReference<EmitterProcessor<HttpContent>> publisher = new AtomicReference<>();
    private final Promise<Tuple2<HttpResponse, Publisher<HttpContent>>> promise;

    public ResponseHandler(Promise<Tuple2<HttpResponse, Publisher<HttpContent>>> promise) {
        this.promise = promise;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpResponse) {
            EmitterProcessor<HttpContent> publisher = EmitterProcessor.create();
            this.publisher.set(publisher);
            promise.setSuccess(Tuple.of((HttpResponse) msg, publisher));
        } else if (msg instanceof LastHttpContent) {
            EmitterProcessor<HttpContent> emitterProcessor = publisher.get();
            emitterProcessor.onNext(((HttpContent) msg).retain());
            emitterProcessor.onComplete();
            ctx.pipeline().remove(this);
        } else if (msg instanceof HttpContent) {
            publisher.get().onNext(((HttpContent) msg).retain());
        } else {
            RuntimeException cause = new RuntimeException(String.format("Unexpected type of message - %s", msg.getClass().getSimpleName()));
            if (!promise.isDone()) {
                promise.setFailure(cause);
            }
            publisher.get().onError(cause);
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!promise.isDone()) {
            promise.setFailure(cause);
        }
        publisher.get().onError(cause);
        ctx.pipeline().remove(this);
        super.exceptionCaught(ctx, cause);
    }
}
