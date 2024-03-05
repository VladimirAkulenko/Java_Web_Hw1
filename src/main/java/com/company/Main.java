package com.company;

public class Main {
    public static void main(String[] args) {
        Server server = new Server();
        server.addHandler("GET", "/messages", (request, out) -> {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        });
        server.addHandler("POST", "/messages", (request, out) -> {
            out.write((
                    "HTTP/1.1 503 Service Unavailable\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        });
        server.listen(9999);
    }

}
