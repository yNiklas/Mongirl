package de.yniklas.mongirl.exception;

public class MongirlDecodeException extends RuntimeException {
    public static String NO_DEFAULT_CONSTRICTOR = "The class %s must have a default constructor, but hasn't";

    public MongirlDecodeException(String message) {
        super(message);
    }
}