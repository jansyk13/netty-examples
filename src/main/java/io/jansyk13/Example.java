package io.jansyk13;

import io.jansyk13.echo.HttpAggregatingEcho;
import io.jansyk13.echo.HttpEcho;
import io.jansyk13.echo.TcpEcho;
import io.jansyk13.pingpong.TcpPingPong;
import io.jansyk13.proxy.TcpProxy;

public class Example {

    public static void main(String[] args) throws Exception {
        TcpEcho.run();
        HttpAggregatingEcho.run();
        HttpEcho.run();

        TcpPingPong.run();

        TcpProxy.run();

//        Server server = null;
//        try {
//            server = new TcpPingPongServer();
//            for (; ; ) {
//                Thread.yield();
//                Thread.sleep(1000);
//            }
//        } finally {
//            if (server != null) {
//                server.close();
//            }
//        }
    }
}
