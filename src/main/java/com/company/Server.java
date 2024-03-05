package com.company;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Server {
    private final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private static final int POOL_SIZE = 64;
    private ExecutorService executeIt;
    private final ConcurrentHashMap<String, Map<String, Handler>> handlers;

    public Server() {
        executeIt = Executors.newFixedThreadPool(POOL_SIZE);
        handlers = new ConcurrentHashMap<>();
    }


    public void listen(int port) {
        try (final ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                final Socket socket = serverSocket.accept();
                executeIt.submit(() -> connect(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executeIt.shutdown();
        }
    }

    public void connect(Socket socket) {
        try (
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final String requestLine = in.readLine();
            final String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                return;
            }

            final String path = parts[1];
            final String method = parts[0];

            if (!handlers.containsKey(method)) {
                out.write((
                        "HTTP/1.1 404 Bed Request\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                return;
            }

            Request request = new Request(method, path);
            Map<String, Handler> handlersMap = handlers.get(method);
            if (handlersMap.containsKey(path)) {
                Handler handler = handlersMap.get(path);
                handler.handle(request, out);
            } else {
                if (!validPaths.contains(path)) {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    return;
                } else {

                    final Path filePath = Path.of(".", "public", path);
                    final String mimeType = Files.probeContentType(filePath);

                    // special case for classic
                    if (path.equals("/classic.html")) {
                        final String template = Files.readString(filePath);
                        final byte[] content = template.replace(
                                "{time}",
                                LocalDateTime.now().toString()
                        ).getBytes();
                        out.write((
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + mimeType + "\r\n" +
                                        "Content-Length: " + content.length + "\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());
                        out.write(content);
                        out.flush();
                        return;
                    }

                    final long length = Files.size(filePath);
                    out.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    Files.copy(filePath, out);
                    out.flush();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

}

