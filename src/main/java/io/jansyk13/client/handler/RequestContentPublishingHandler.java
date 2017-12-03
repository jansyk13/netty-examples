package io.jansyk13.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RequestContentPublishingHandler extends SimpleChannelInboundHandler {
    //TODO replace with field updater
    private AtomicReference<Subscription> subscription = new AtomicReference<>();
    AtomicInteger done = new AtomicInteger(0);

    private final Publisher<HttpContent> publisher;
    private final Promise<?> promise;
    private final boolean expectContinue;

    public RequestContentPublishingHandler(Publisher<HttpContent> publisher, Promise<?> promise, boolean expectContinue) {
        this.publisher = publisher;
        this.promise = promise;
        this.expectContinue = expectContinue;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (!expectContinue) {
            publishContent(ctx);
        }
        super.handlerAdded(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!promise.isDone()) {
            promise.setFailure(new RuntimeException("Unexpected channel read in RequestContentPublishingHandler"));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // remove and propagate
        cancelIfNotDone();
        ctx.pipeline().remove(this);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // remove and propagate
        cancelIfNotDone();
        ctx.pipeline().remove(this);
        super.exceptionCaught(ctx, cause);
    }

    private void publishContent(ChannelHandlerContext ctx) {
        this.publisher.subscribe(new Subscriber<HttpContent>() {
            //TODO replace with field updater

            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
                subscription.get().request(1);
            }

            @Override
            public void onNext(HttpContent httpContent) {
                if (done.get() == 0) {
                    // TODO flush on bigger pieces?
                    ctx.writeAndFlush(httpContent);
                    subscription.get().request(1);
                }
            }

            @Override
            public void onError(Throwable t) {
                done.getAndAccumulate(1, (old, neww) -> {
                    if (old == 0) {
                        subscription.get().cancel();
                        ctx.fireExceptionCaught(t);
                        promise.setFailure(t);
                        return neww;
                    }
                    return old;
                });
            }

            @Override
            public void onComplete() {
                // remove from pipeline when done
                ctx.pipeline().remove(RequestContentPublishingHandler.class);
                cancelIfNotDone();
            }
        });
    }

    private void cancelIfNotDone() {
        done.getAndAccumulate(1, (old, neww) -> {
            if (old == 0) {
                subscription.get().cancel();
                return neww;
            }
            return old;
        });
    }
}
