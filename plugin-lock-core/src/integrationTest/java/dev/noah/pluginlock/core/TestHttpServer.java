package dev.noah.pluginlock.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class TestHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, Response> responses = new HashMap<>();
    private int requestCount;

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new Handler());
        server.start();
    }

    public URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    public int requestCount() {
        return requestCount;
    }

    public void json(String path, String body) {
        response(path, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    public void bytes(String path, byte[] body) {
        response(path, 200, "application/octet-stream", body);
    }

    public void response(String path, int status, String contentType, byte[] body) {
        responses.put(path, new Response(status, contentType, body));
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount++;
            Response response = responses.get(exchange.getRequestURI().toString());
            if (response == null) {
                response = new Response(404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            }
            exchange.getResponseHeaders().add("Content-Type", response.contentType);
            exchange.sendResponseHeaders(response.status, response.body.length);
            exchange.getResponseBody().write(response.body);
            exchange.close();
        }
    }

    private record Response(int status, String contentType, byte[] body) {
    }
}
