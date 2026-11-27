package org.loginsight.core.replay;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds stable request timelines and minute-level error summaries from imported records. */
public final class ReplayAnalyzer {
    private static final Comparator<ReplayRecord> EVENT_ORDER = Comparator
            .comparing(ReplayRecord::timestamp)
            .thenComparingLong(ReplayRecord::importOrder)
            .thenComparingInt(ReplayRecord::lineNumber);

    private final List<ReplayRecord> records;

    public ReplayAnalyzer(List<ReplayRecord> records) {
        Objects.requireNonNull(records, "records");
        this.records = new ArrayList<>(records);
        this.records.forEach(record -> Objects.requireNonNull(record, "record"));
        this.records.sort(EVENT_ORDER);
    }

    public List<ErrorBucket> errorBuckets() {
        Map<Instant, Integer> counts = new LinkedHashMap<>();
        for (ReplayRecord record : records) {
            if ("ERROR".equals(record.level())) {
                Instant minute = record.timestamp().truncatedTo(ChronoUnit.MINUTES);
                counts.merge(minute, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new ErrorBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<String> failedRequestIds(Instant minuteStart) {
        Objects.requireNonNull(minuteStart, "minuteStart");
        Instant minute = minuteStart.truncatedTo(ChronoUnit.MINUTES);
        LinkedHashSet<String> requestIds = new LinkedHashSet<>();
        for (ReplayRecord record : records) {
            if ("ERROR".equals(record.level())
                    && record.timestamp().truncatedTo(ChronoUnit.MINUTES).equals(minute)) {
                requestIds.add(record.requestId());
            }
        }
        return List.copyOf(requestIds);
    }

    public List<ReplayRecord> timeline(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        return records.stream()
                .filter(record -> requestId.equals(record.requestId()))
                .toList();
    }
}
