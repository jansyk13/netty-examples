package io.jansyk13.echo

import io.jansyk13.client.SocketClient
import io.netty.util.concurrent.Promise
import spock.lang.Specification
import spock.lang.Unroll

import java.util.stream.Collectors

@Unroll
class SocketEchoSpec extends Specification {

    def "socket echo"() {
        given:
        def server = new SocketEchoServer()
        def client = new SocketClient()

        when:
        def write = client.write(content).sync()

        then:
        write.get() == content

        cleanup:
        server.close()
        client.close()

        where:
        content << ["echo", "troll", "bobika"]
    }

    def "reusing client for socket echo"() {
        given:
        def server = new SocketEchoServer()
        def client = new SocketClient()

        when:
        List<Promise<String>> writes = (0..100).stream().map {
            client.write("${it}").sync()
        }.collect(Collectors.toList())

        then:
        writes.withIndex().stream().map { promise, i -> promise.get() == "${i}" }.reduce { l, r -> l && r }

        cleanup:
        server.close()
        client.close()
    }
}
