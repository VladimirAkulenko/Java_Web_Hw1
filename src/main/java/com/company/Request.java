package com.company;

public class Request {
    private final String method;
    private final String path;

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
    }
}
