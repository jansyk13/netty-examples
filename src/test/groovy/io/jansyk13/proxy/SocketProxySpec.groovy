package io.jansyk13.proxy

import io.jansyk13.client.SocketClient
import io.jansyk13.echo.SocketEchoServer
import spock.lang.Specification

class SocketProxySpec extends Specification {

    def 'test'() {
        given:
        def echo = new SocketEchoServer(7777)
        def proxy = new SocketProxyServer(8080, 7777)
        def client = new SocketClient(8080)

        when:
        def promise = client.write("troll")

        then:
        promise.get() == "troll"
        
        cleanup:
        echo.close()
        proxy.close()
        client.close()
    }
}
