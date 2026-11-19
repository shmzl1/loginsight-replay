package org.loginsight.plugin.jsonl;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlLogParserTest {
    private final LogParser parser = new JsonlLogParser();

    @Test
    void formatIdentifiesJsonl() {
        assertEquals("jsonl", parser.format());
    }

    @Test
    void mapsValidObjectToEvent() throws ParseException {
        String line = "{\"timestamp\":\"2026-05-12T08:00:04Z\",\"requestId\":\"R-17\","
                + "\"service\":\"quota\",\"level\":\"ERROR\",\"message\":\"获取连接超时\"}";

        ParsedEvent event = parser.parse(line);

        assertEquals(Instant.parse("2026-05-12T08:00:04Z"), event.timestamp());
        assertEquals("R-17", event.requestId());
        assertEquals("quota", event.service());
        assertEquals("ERROR", event.level());
        assertEquals("获取连接超时", event.message());
    }

    @Test
    void normalizesLevelToUppercase() throws ParseException {
        String line = "{\"timestamp\":\"2026-05-12T08:00:03Z\",\"requestId\":\"R-17\","
                + "\"service\":\"quota\",\"level\":\"info\",\"message\":\"等待数据库连接\"}";

        assertEquals("INFO", parser.parse(line).level());
    }

    @Test
    void rejectsBlankLine() {
        ParseException error = assertThrows(ParseException.class, () -> parser.parse(" "));
        assertEquals("line is empty", error.getMessage());
    }

    @Test
    void rejectsMalformedJson() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse("{\"timestamp\":\"2026-05-12T08:00:04Z\","));
        assertTrue(error.getMessage().startsWith("malformed JSON:"));
    }

    @Test
    void rejectsMissingField() {
        String line = "{\"timestamp\":\"2026-05-12T08:00:04Z\",\"requestId\":\"R-17\","
                + "\"service\":\"quota\",\"message\":\"获取连接超时\"}";

        ParseException error = assertThrows(ParseException.class, () -> parser.parse(line));
        assertEquals("missing field: level", error.getMessage());
    }

    @Test
    void rejectsBlankField() {
        String line = "{\"timestamp\":\"2026-05-12T08:00:04Z\",\"requestId\":\"  \","
                + "\"service\":\"quota\",\"level\":\"ERROR\",\"message\":\"获取连接超时\"}";

        ParseException error = assertThrows(ParseException.class, () -> parser.parse(line));
        assertEquals("field is blank: requestId", error.getMessage());
    }

    @Test
    void rejectsInvalidTimestamp() {
        String line = "{\"timestamp\":\"not-a-time\",\"requestId\":\"R-17\","
                + "\"service\":\"quota\",\"level\":\"ERROR\",\"message\":\"获取连接超时\"}";

        ParseException error = assertThrows(ParseException.class, () -> parser.parse(line));
        assertEquals("invalid timestamp: not-a-time", error.getMessage());
    }

    @Test
    void discoversParserThroughServiceLoader() {
        LogParser loaded = ServiceLoader.load(LogParser.class, getClass().getClassLoader())
                .findFirst().orElseThrow();

        assertInstanceOf(JsonlLogParser.class, loaded);
        assertEquals("jsonl", loaded.format());
    }

    @Test
    void discoversParserFromPackagedJar() throws Exception {
        String jarPath = System.getProperty("plugin.jar");
        if (jarPath == null || jarPath.isBlank()) {
            return;
        }

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { new File(jarPath).toURI().toURL() },
                JsonlLogParserTest.class.getClassLoader())) {
            LogParser loaded = ServiceLoader.load(LogParser.class, loader)
                    .findFirst().orElseThrow();
            assertInstanceOf(JsonlLogParser.class, loaded);
            assertEquals("jsonl", loaded.format());
        }
    }
}
