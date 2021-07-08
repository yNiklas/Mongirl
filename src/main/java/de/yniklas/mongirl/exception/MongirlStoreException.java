package de.yniklas.mongirl.exception;

public class MongirlStoreException extends RuntimeException {
    public static final String NO_CODEC = "An object cannot be encoded and couldn't be stored. Details: %s";

    public MongirlStoreException(String message) {
        super(message);
    }
}
