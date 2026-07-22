package com.backend.code.editor.exception;

public class ExecutionFailedException extends RuntimeException {

    public ExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
