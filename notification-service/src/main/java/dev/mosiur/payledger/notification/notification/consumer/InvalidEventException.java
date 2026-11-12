package dev.mosiur.payledger.notification.notification.consumer;

/** The message can never be processed as it is, so retrying is pointless: it goes straight to the dead letter topic. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
