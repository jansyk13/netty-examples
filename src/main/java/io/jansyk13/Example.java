package io.jansyk13;

import io.jansyk13.echo.HttpAggregatingEcho;
import io.jansyk13.echo.HttpEcho;
import io.jansyk13.echo.TcpEcho;
import io.jansyk13.pingpong.TcpPingPong;

public class Example {

    public static void main(String[] args) throws Exception {
        TcpEcho.run();
        HttpAggregatingEcho.run();
        HttpEcho.run();

        TcpPingPong.run();

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
