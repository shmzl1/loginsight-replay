package org.loginsight.core.replay;

import java.time.Instant;
import java.util.Objects;

/** The number of ERROR events in one UTC minute. */
public record ErrorBucket(Instant minuteStart, int errorCount) {
    public ErrorBucket {
        Objects.requireNonNull(minuteStart, "minuteStart");
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
        }
    }
}
