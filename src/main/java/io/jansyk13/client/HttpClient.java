package io.jansyk13.client;

import io.jansyk13.client.handler.RequestContentPublishingHandler;
import io.jansyk13.client.handler.ResponseHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Promise;
import io.vavr.Tuple2;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.io.IOException;

public class HttpClient implements Closeable {

    private static final String NETTY_CLIENT_HTTP = "netty-client-http";
    private final EpollEventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ChannelFuture channelFuture;

    public HttpClient() {
        this(7777);
    }

    public HttpClient(int port) {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory(NETTY_CLIENT_HTTP));
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
        try {
            this.channelFuture = bootstrap.connect("localhost", port).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Promise<Tuple2<HttpResponse, Publisher<HttpContent>>> write(HttpRequest request, Publisher<HttpContent> publisher) {
        Channel channel = this.channelFuture.channel();

        DefaultPromise<Tuple2<HttpResponse, Publisher<HttpContent>>> promise = new DefaultPromise<>(channel.eventLoop());

        // send request
        channel.writeAndFlush(request)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        boolean expectContinue = request.headers().get(HttpHeaderNames.EXPECT) != null;

        // subscribe on request content publisher - sends http content
        channel.pipeline().addLast(new RequestContentPublishingHandler(publisher, promise, expectContinue));

        // handle inbound http data
        channel.pipeline().addLast(new ResponseHandler(promise));

        return promise;
    }

    @Override
    public void close() throws IOException {
        this.channelFuture.channel().closeFuture();
        this.eventLoopGroup.shutdownGracefully();
    }
}
