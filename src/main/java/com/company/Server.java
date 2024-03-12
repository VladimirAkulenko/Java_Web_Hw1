package com.company;


import org.apache.http.NameValuePair;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
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
                final BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {

            Request request = Request.createRequest(in);
            if (request == null || !handlers.containsKey(request.getMethod())) {
                out.write((
                        "HTTP/1.1 404 Bed Request\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                return;
            } else {
                System.out.println("Request debug information: ");
                System.out.println("METHOD: " + request.getMethod());
                System.out.println("PATH: " + request.getPath());
                System.out.println("HEADERS: " + request.getHeaders());
                System.out.println("Query Params:");
                for (var para : request.getQueryParams()) {
                    System.out.println(para.getName() + " = " + para.getValue());
                }

                System.out.println("Test for dumb param name:");
                System.out.println(request.getQueryParam("YetAnotherDumb").getName());
                System.out.println("Test for dumb param name-value:");
                System.out.println(request.getQueryParam("testDebugInfo").getValue());
            }

            Map<String, Handler> handlersMap = handlers.get(request.getMethod());
            if (handlersMap.containsKey(request.getPath())) {
                Handler handler = handlersMap.get(request.getPath());
                handler.handle(request, out);
            } else {
                if (!validPaths.contains(request.getPath())) {
                    out.write((
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                    return;
                } else {

                    final Path filePath = Path.of(".", "public", request.getPath());
                    final String mimeType = Files.probeContentType(filePath);

                    // special case for classic
                    if (request.getPath().equals("/classic.html")) {
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

        } catch (IOException | URISyntaxException e) {
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
