package org.loginsight.core.replay;

import java.time.Instant;
import java.util.Objects;
import org.loginsight.api.ParsedEvent;

/** A parsed event together with the source details needed for replay and inspection. */
public record ReplayRecord(Instant timestamp, String requestId, String service, String level,
                           String message, String sourceId, String sourceFile, int lineNumber,
                           String rawLine, long importOrder) {
    public ReplayRecord {
        Objects.requireNonNull(timestamp, "timestamp");
        requireText(requestId, "requestId");
        requireText(service, "service");
        requireText(level, "level");
        requireText(message, "message");
        requireText(sourceId, "sourceId");
        requireText(sourceFile, "sourceFile");
        Objects.requireNonNull(rawLine, "rawLine");
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
        if (importOrder < 0) {
            throw new IllegalArgumentException("importOrder must not be negative");
        }
    }

    public static ReplayRecord from(ParsedEvent event, String sourceId, String sourceFile,
                                    int lineNumber, String rawLine, long importOrder) {
        Objects.requireNonNull(event, "event");
        return new ReplayRecord(event.timestamp(), event.requestId(), event.service(), event.level(),
                event.message(), sourceId, sourceFile, lineNumber, rawLine, importOrder);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
