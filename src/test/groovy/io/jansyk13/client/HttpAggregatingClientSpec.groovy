package io.jansyk13.client

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.CharsetUtil
import net.jadler.Jadler
import net.jadler.stubbing.server.jdk.JdkStubHttpServer
import spock.lang.Specification

import static net.jadler.Jadler.closeJadler
import static net.jadler.Jadler.initJadlerUsing
import static net.jadler.Jadler.onRequest

class HttpAggregatingClientSpec extends Specification {

    def 'test'() {
        given:
        initJadlerUsing(new JdkStubHttpServer());

        onRequest().havingMethodEqualTo("POST")
                .havingPathEqualTo("/test")
                .havingBodyEqualTo("foo")
                .respond()
                .withBody("bar")

        def client = new HttpAggregatingClient(Jadler.port())

        def byteBuf = Unpooled.copiedBuffer("foo", CharsetUtil.UTF_8)

        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/test", byteBuf)
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes())

        when:
        def response = client.write(request).sync().get()

        then:
        response.status().code() == 200
        response.content().toString(CharsetUtil.UTF_8) == "bar"

        cleanup:
        response.content().release()
        client.close()
        closeJadler()
    }
}
