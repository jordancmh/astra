package com.astra.engine.routing;

public class GraphHopperServiceException extends RuntimeException {
    public GraphHopperServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphHopperServiceException(String message) {
        super(message);
    }
}
