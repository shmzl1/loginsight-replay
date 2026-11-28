package org.loginsight.core.text;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;

/** Parses the built-in whitespace-delimited log format. */
public final class TextLogParser implements LogParser {
    @Override
    public String format() {
        return "text";
    }

    @Override
    public ParsedEvent parse(String line) throws ParseException {
        if (line == null || line.isBlank()) {
            throw new ParseException("line is empty");
        }

        String[] fields = splitFields(line);
        if (fields == null) {
            throw new ParseException("expected timestamp, requestId, service, level and message");
        }

        Instant timestamp;
        try {
            timestamp = OffsetDateTime.parse(fields[0]).toInstant();
        } catch (DateTimeParseException exception) {
            throw new ParseException("invalid timestamp: " + fields[0], exception);
        }

        try {
            return new ParsedEvent(timestamp, fields[1], fields[2],
                    fields[3].toUpperCase(java.util.Locale.ROOT), fields[4]);
        } catch (IllegalArgumentException exception) {
            throw new ParseException("invalid log fields: " + exception.getMessage(), exception);
        }
    }

    private String[] splitFields(String line) {
        String input = line.stripLeading();
        String[] fields = new String[5];
        int start = 0;
        for (int field = 0; field < 4; field++) {
            int end = start;
            while (end < input.length() && !Character.isWhitespace(input.charAt(end))) {
                end++;
            }
            if (end == start || end == input.length()) {
                return null;
            }
            fields[field] = input.substring(start, end);
            start = end;
            while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
                start++;
            }
        }
        if (start == input.length()) {
            return null;
        }
        fields[4] = input.substring(start).stripTrailing();
        if (fields[4].isBlank()) {
            return null;
        }
        return fields;
    }
}
