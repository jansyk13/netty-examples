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
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Promise;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.Closeable;
import java.io.IOException;

public class HttpClient implements Closeable {

    private static final String NETTY_CLIENT_AGGR_HTTP = "netty-client-http";
    private final EpollEventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ChannelFuture channelFuture;

    public HttpClient() {
        this(7777);
    }

    public HttpClient(int port) {
        this.eventLoopGroup = new EpollEventLoopGroup(4, new DefaultThreadFactory(NETTY_CLIENT_AGGR_HTTP));
        this.bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInitializer<EpollSocketChannel>() {
                    @Override
                    protected void initChannel(EpollSocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LoggingHandler(LogLevel.INFO));
                        pipeline.addLast(new HttpClientCodec());
                    }
                });
        try {
            this.channelFuture = bootstrap.connect("localhost", port).sync();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Promise<HttpResponse> write(HttpRequest request, Publisher<HttpContent> publisher, Subscriber<HttpContent> subscriber) {
        Channel channel = this.channelFuture.channel();

        DefaultPromise<HttpResponse> promise = new DefaultPromise<>(channel.eventLoop());

        // send request
        channel.writeAndFlush(request)
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        // subscribe on request content publisher - sends http content
        channel.pipeline().addLast(new RequestContentPublishingHandler(channel, publisher, promise));

        // handle inbound http data
        channel.pipeline().addLast(new ResponseHandler(subscriber, promise));

        return promise;
    }

    @Override
    public void close() throws IOException {
        this.channelFuture.channel().closeFuture();
        this.eventLoopGroup.shutdownGracefully();
    }
}