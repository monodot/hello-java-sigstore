package xyz.tomd.demos.hellorestjava;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Hello world!
 * Based on the Undertow examples: https://github.com/undertow-io/undertow/tree/master/examples
 *
 */
public class Application {

    public static void main(String[] args) {
        Undertow server = Undertow.builder()
                .addHttpListener(8080, "0.0.0.0")
                .setHandler(exchange -> {
                    exchange.getResponseHeaders()
                            .put(Headers.CONTENT_TYPE, "text/html");
                    exchange.getResponseSender()
                            .send("<html>" +
                                    "<body>" +
                                    "<h1>Hello, world!</h1>" +
                                    "<p>Some extra text here, for your enjoyment!!!</p>" +
                                    "<p>Nightmare on Elm Street is the best horror movie.</p>" +
                                    "</body>" +
                                    "</html>");
                }).build();
        server.start();
    }
}
