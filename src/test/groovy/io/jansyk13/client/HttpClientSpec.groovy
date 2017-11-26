package io.jansyk13.client

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.CharsetUtil
import net.jadler.Jadler
import net.jadler.stubbing.server.jdk.JdkStubHttpServer
import reactor.core.publisher.Flux
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

        when:
        def write = client.write(request, publisher).sync()

        then:
        write.get()._1().status().code() == 200
        def content = Mono.from(write.get()._2()).block().content()
        content.toString(CharsetUtil.UTF_8) == "bar"
        content.release()

        cleanup:
        client.close()
        closeJadler()
    }
}
