package org.loginsight.plugin.jsonl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;

/** Parses one JSONL object per line into the unified parsed event. */
public final class JsonlLogParser implements LogParser {
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_REQUEST_ID = "requestId";
    private static final String FIELD_SERVICE = "service";
    private static final String FIELD_LEVEL = "level";
    private static final String FIELD_MESSAGE = "message";

    @Override
    public String format() {
        return "jsonl";
    }

    @Override
    public ParsedEvent parse(String line) throws ParseException {
        if (line == null || line.isBlank()) {
            throw new ParseException("line is empty");
        }

        JSONObject object = parseObject(line);
        Instant timestamp = parseTimestamp(object);
        String requestId = requireText(object, FIELD_REQUEST_ID);
        String service = requireText(object, FIELD_SERVICE);
        String level = requireText(object, FIELD_LEVEL).toUpperCase(Locale.ROOT);
        String message = requireText(object, FIELD_MESSAGE);

        try {
            return new ParsedEvent(timestamp, requestId, service, level, message);
        } catch (IllegalArgumentException exception) {
            throw new ParseException("invalid log fields: " + exception.getMessage(), exception);
        }
    }

    /** Accepts exactly one JSON object; anything left after the closing brace is a bad line. */
    private JSONObject parseObject(String line) throws ParseException {
        JSONTokener tokener = new JSONTokener(line);
        try {
            JSONObject object = new JSONObject(tokener);
            if (tokener.nextClean() != 0) {
                throw new ParseException("trailing content after JSON object");
            }
            return object;
        } catch (JSONException exception) {
            throw new ParseException("malformed JSON: " + exception.getMessage(), exception);
        }
    }

    private Instant parseTimestamp(JSONObject object) throws ParseException {
        String raw = requireText(object, FIELD_TIMESTAMP);
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException exception) {
            throw new ParseException("invalid timestamp: " + raw, exception);
        }
    }

    /** Requires a present, non-null, non-blank string; reports any other type by field name. */
    private String requireText(JSONObject object, String field) throws ParseException {
        if (!object.has(field) || object.isNull(field)) {
            throw new ParseException("missing field: " + field);
        }
        Object value = object.get(field);
        if (!(value instanceof String text)) {
            throw new ParseException("field is not a string: " + field);
        }
        if (text.isBlank()) {
            throw new ParseException("field is blank: " + field);
        }
        return text;
    }
}
