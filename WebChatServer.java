
package chatapp;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;

/*
 Simple HTTP server with:
  - Serves static files from /web folder
  - POST /send  -> accept JSON {name,message}
  - GET  /events -> Server-Sent Events (SSE) for broadcasting messages to browser clients
*/
public class WebChatServer {
    private static final int PORT = 8000;
    private static final List<SseClient> clients = Collections.synchronizedList(new LinkedList<>());

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/send", new SendHandler());
        server.createContext("/events", new EventsHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        System.out.println("Web chat server started on http://localhost:" + PORT);
        server.start();
    }

    static void broadcast(String msg) {
        synchronized (clients) {
            for (SseClient c : clients) {
                try {
                    c.send(msg);
                } catch (IOException e) {
                    // ignore send errors; client will be removed when next write fails
                }
            }
        }
    }

    static class SseClient {
        private final OutputStream os;
        private final PrintWriter pw;
        SseClient(OutputStream os) {
            this.os = os;
            this.pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        }
        void send(String data) throws IOException {
            // send SSE event 'message'
            pw.print("event: message\n");
            // split lines
            for (String line : data.split("\\n")) {
                pw.print("data: " + line + "\n");
            }
            pw.print("\n"); // end of event
            pw.flush();
        }
        void close() {
            try { os.close(); } catch (IOException ignored) {}
        }
    }

    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers h = exchange.getResponseHeaders();
            h.set("Content-Type", "text/event-stream; charset=UTF-8");
            h.set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            SseClient client = new SseClient(exchange.getResponseBody());
            clients.add(client);
            try {
                // keep connection open by sleeping; will be removed on disconnect
                while (true) {
                    try { Thread.sleep(60000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception ignored) {
            } finally {
                clients.remove(client);
                client.close();
            }
        }
    }

    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // expect JSON {name:..., message:...}
            String name = "Anon";
            String message = body;
            // naive parse (no external libs)
            try {
                body = body.trim();
                if (body.startsWith("{")) {
                    int n1 = body.indexOf("\"name\"")>=0? body.indexOf("\"name\"") : body.indexOf("name");
                    int m1 = body.indexOf("\"message\"")>=0? body.indexOf("\"message\"") : body.indexOf("message");
                    if (n1 >= 0) {
                        int c = body.indexOf(':', n1);
                        int start = body.indexOf('"', c);
                        int end = body.indexOf('"', start+1);
                        if (start>=0 && end>start) name = body.substring(start+1, end);
                    }
                    if (m1 >= 0) {
                        int c = body.indexOf(':', m1);
                        int start = body.indexOf('"', c);
                        int end = body.indexOf('"', start+1);
                        if (start>=0 && end>start) message = body.substring(start+1, end);
                    }
                }
            } catch (Exception ignored) {}
            String formatted = name + ": " + message;
            System.out.println("Broadcast: " + formatted);
            broadcast(formatted);
            byte[] resp = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp);
            os.close();
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File f = new File("web" + path.replace("..",""));
            if (!f.exists() || f.isDirectory()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String type = "text/plain";
            if (path.endsWith(".html")) type = "text/html; charset=UTF-8";
            else if (path.endsWith(".js")) type = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".css")) type = "text/css; charset=UTF-8";
            exchange.getResponseHeaders().set("Content-Type", type);
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
