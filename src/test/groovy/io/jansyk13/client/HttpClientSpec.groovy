package io.jansyk13.client

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.CharsetUtil
import net.jadler.Jadler
import net.jadler.stubbing.server.jdk.JdkStubHttpServer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Mono
import spock.lang.Specification

import static net.jadler.Jadler.closeJadler
import static net.jadler.Jadler.initJadlerUsing
import static net.jadler.Jadler.onRequest

class HttpClientSpec extends Specification {


    def 'test'() {
        given:
        initJadlerUsing(new JdkStubHttpServer());

        onRequest().havingMethodEqualTo("POST")
                .havingPathEqualTo("/test")
                .havingBodyEqualTo("foo")
                .respond()
                .withBody("bar")

        def client = new HttpClient(Jadler.port())

        def byteBuf = Unpooled.copiedBuffer("foo", CharsetUtil.UTF_8)
        def publisher = Mono.just(new DefaultHttpContent(byteBuf))

        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/test")
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())

        def subscriber = new Subscriber<HttpContent>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(HttpContent msg) {
                assert msg.content().toString(CharsetUtil.UTF_8) == "bar"
            }

            @Override
            void onError(Throwable t) {
                throw t
            }

            @Override
            void onComplete() {

            }
        }

        when:
        def write = client.write(request, publisher, subscriber).sync()

        then:
        write.get().status().code() == 200

        cleanup:
        client.close()
        closeJadler()
    }
}
