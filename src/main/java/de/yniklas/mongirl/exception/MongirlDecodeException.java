package de.yniklas.mongirl.exception;

public class MongirlDecodeException extends RuntimeException {
    public static String NO_CONSTRICTOR
            = "The class %s must have a public constructor";

    public MongirlDecodeException(String message) {
        super(message);
    }
}
