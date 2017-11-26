package io.jansyk13.echo

import io.jansyk13.client.HttpAggregatingClient
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.CharsetUtil
import io.netty.util.concurrent.Promise
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

@Unroll
class HttpAggregatingEchoSpec extends Specification {

    def "http echo"() {
        given:
        def server = new HttpAggregatingEchoServer()
        def client = new HttpAggregatingClient()
        def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", Unpooled.copiedBuffer(content, CharsetUtil.UTF_8))
        request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes())

        when:
        Promise<FullHttpResponse> write = client.write(request).sync()

        then:
        with(write.get()) { it ->
            it.content().toString(CharsetUtil.UTF_8) == content
            it.status().code == 200
        }

        cleanup:
        server.close()
        client.close()

        where:
        content << ["echo", "bobika", "troll"]
    }

    def "http echo reuse"() {
        given:
        def server = new HttpAggregatingEchoServer()
        def client = new HttpAggregatingClient()

        when:
        List<Promise<FullHttpResponse>> writes = (1..100).stream().map {
            def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", Unpooled.copiedBuffer("${it}", CharsetUtil.UTF_8))
            request.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes())
            client.write(request).sync()
        }.collect(Collectors.toList())

        then:
        writes.collect { it -> it.get() }
                .withIndex()
                .stream()
                .map { response, i -> response.content().toString(CharsetUtil.UTF_8) == "${i}" && response.status().code == 200 }
                .reduce { l, r -> l && r }

        cleanup:
        server.close()
        client.close()
    }
}
