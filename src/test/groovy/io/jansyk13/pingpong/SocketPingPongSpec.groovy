package io.jansyk13.pingpong

import io.jansyk13.client.SocketClient
import io.netty.util.concurrent.Promise
import spock.lang.Specification
import spock.lang.Unroll

import static java.util.stream.Collectors.toList

@Unroll
class SocketPingPongSpec extends Specification {

    def "ping pong"() {
        given:
        def server = new SocketPingPongServer()
        def client = new SocketClient()

        when:
        def pong = client.write(req).sync()

        then:
        pong.get() == res

        cleanup:
        server.close()
        client.close()

        where:
        req    | res
        "ping" | "pong"
        "foo"  | "Not ping - foo"
    }

    def "ping pong reuse"() {
        given:
        def server = new SocketPingPongServer()
        def client = new SocketClient()

        and:
        def req = ["ping", "ping", "foo", "ping"]
        def res = ["pong", "pong", "Not ping - foo", "ping"]

        when:
        List<Promise<String>> writes = req.stream().map { client.write(it).sync() }.collect(toList())

        then:
        writes.withIndex().stream().map { promise, i -> promise.get() == res[i] }.reduce { l, r -> l && r }

        cleanup:
        server.close()
        client.close()
    }
}
