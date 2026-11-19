package org.loginsight.api;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Parsed fields only. The host attaches the source, physical line number and raw line. */
public record ParsedEvent(Instant timestamp, String requestId, String service,
                          String level, String message) {
    public ParsedEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        requireText(requestId, "requestId");
        requireText(service, "service");
        requireText(level, "level");
        requireText(message, "message");
        if (!level.equals(level.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("level must be uppercase");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
