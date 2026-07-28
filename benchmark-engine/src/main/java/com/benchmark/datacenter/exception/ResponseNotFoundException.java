package com.benchmark.datacenter.exception;

import java.util.UUID;

public class ResponseNotFoundException extends RuntimeException {
    public ResponseNotFoundException(UUID id) {
        super("No existe una respuesta de benchmark con id " + id);
    }
}
