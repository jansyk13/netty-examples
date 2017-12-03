package io.jansyk13.client;

import io.jansyk13.client.handler.ReleaseHandler;
import io.jansyk13.client.handler.RequestContentPublishingHandler;
import io.jansyk13.client.handler.ResponseHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.vavr.Tuple2;
import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public class PooledHttpClient {

    private static final String NETTY_POOLED_HTTP_CLIENT = "netty-pooled-http-client";
    private final int port;
    private final int poolSize;
    private final EpollEventLoopGroup eventLoopGroup;
    private final FixedChannelPool channelPool;
    private final Bootstrap bootstrap;
    private final ChannelPoolHandler poolHandler;

    public PooledHttpClient(int port, int poolSize) {
        this.port = port;
        this.poolSize = poolSize;
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory(NETTY_POOLED_HTTP_CLIENT));
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                    }
                });
        this.poolHandler = new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel ch) throws Exception {

            }
        };
        this.channelPool = new FixedChannelPool(this.bootstrap, this.poolHandler, this.poolSize);
    }

    Promise<Tuple2<HttpResponse, Publisher<HttpContent>>> writeAndRead(HttpRequest httpRequest, Publisher<HttpContent> requestContentPublisher) {
        DefaultPromise<Tuple2<HttpResponse, Publisher<HttpContent>>> promise = new DefaultPromise<>(this.eventLoopGroup.next());

        this.channelPool.acquire().addListener((Future<? super Channel> future) -> {
            if (future.isSuccess()) {
                Channel channel = (Channel) future.getNow();
                writeAndRead0(channel, promise, httpRequest, requestContentPublisher, this.channelPool::release);
            } else {
                if (future.cause() != null) {
                    promise.setFailure(future.cause());
                } else {
                    promise.setFailure(new HttpException("Can't acquire channel from pool"));
                }
            }
        });

        return promise;
    }

    private void writeAndRead0(Channel channel, DefaultPromise<Tuple2<HttpResponse, Publisher<HttpContent>>> promise,
                               HttpRequest httpRequest, Publisher<HttpContent> requestContentPublisher,
                               Consumer<Channel> releaser) {
        // send request
        channel.writeAndFlush(httpRequest)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        boolean expectContinue = httpRequest.headers().get(HttpHeaderNames.EXPECT) != null;

        // subscribe on request content publisher - sends http content
        channel.pipeline().addLast(new RequestContentPublishingHandler(requestContentPublisher, promise, expectContinue));

        // handle inbound http data
        channel.pipeline().addLast(new ResponseHandler(promise));

        // handle pool release
        channel.pipeline().addLast(new ReleaseHandler(releaser));
    }

    public static class HttpException extends RuntimeException {
        public HttpException() {
        }

        public HttpException(String s) {
            super(s);
        }

        public HttpException(String s, Throwable throwable) {
            super(s, throwable);
        }

        public HttpException(Throwable throwable) {
            super(throwable);
        }

        public HttpException(String s, Throwable throwable, boolean b, boolean b1) {
            super(s, throwable, b, b1);
        }
    }
}
