package org.loginsight.core.text;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;
import org.loginsight.core.ParserRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextLogParserTest {
    private final TextLogParser parser = new TextLogParser();

    @Test
    void parsesFirstCampusSampleLine() throws ParseException {
        ParsedEvent event = parser.parse(
                "2026-05-12T08:00:00Z R-17 campus INFO 收到选课请求");

        assertEquals(new ParsedEvent(Instant.parse("2026-05-12T08:00:00Z"), "R-17", "campus",
                "INFO", "收到选课请求"), event);
    }

    @Test
    void isRegisteredAsTheBuiltInTextProvider() {
        LogParser discovered = new ParserRegistry(getClass().getClassLoader()).select("text");

        assertEquals(TextLogParser.class, discovered.getClass());
    }

    @Test
    void acceptsOffsetTimestampAndNormalizesLevel() throws ParseException {
        ParsedEvent event = parser.parse("2026-05-12T16:00:00+08:00 R-17 campus error 请求失败");

        assertEquals(Instant.parse("2026-05-12T08:00:00Z"), event.timestamp());
        assertEquals("ERROR", event.level());
    }

    @Test
    void reportsMalformedTimestamp() {
        ParseException exception = assertThrows(ParseException.class,
                () -> parser.parse("not-a-time R-17 campus INFO 请求失败"));

        assertEquals("invalid timestamp: not-a-time", exception.getMessage());
    }

    @Test
    void rejectsBlankAndIncompleteLines() {
        assertEquals("line is empty", assertThrows(ParseException.class,
                () -> parser.parse("  ")).getMessage());
        assertEquals("expected timestamp, requestId, service, level and message",
                assertThrows(ParseException.class, () -> parser.parse("2026-05-12T08:00:00Z R-17"))
                        .getMessage());
    }

    @Test
    void aBadLineDoesNotPreventParsingTheNextPhysicalLine() throws ParseException {
        String[] lines = {
            "2026-05-12T08:00:00Z R-17 campus INFO first",
            "bad row",
            "2026-05-12T08:00:02Z R-17 campus INFO third"
        };
        int accepted = 0;
        int failedLine = -1;
        for (int index = 0; index < lines.length; index++) {
            try {
                parser.parse(lines[index]);
                accepted++;
            } catch (ParseException exception) {
                failedLine = index + 1;
            }
        }

        assertEquals(2, accepted);
        assertEquals(2, failedLine);
        assertEquals("third", parser.parse(lines[2]).message());
    }
}
