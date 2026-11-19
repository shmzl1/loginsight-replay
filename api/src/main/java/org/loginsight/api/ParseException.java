package org.loginsight.api;

/** A malformed input line. The host may continue with the next physical line. */
public class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
