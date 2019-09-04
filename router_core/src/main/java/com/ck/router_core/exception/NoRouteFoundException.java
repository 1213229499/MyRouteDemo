package com.ck.router_core.exception;

public class NoRouteFoundException extends RuntimeException{

    public NoRouteFoundException(String message) {
        super(message);
    }
}
