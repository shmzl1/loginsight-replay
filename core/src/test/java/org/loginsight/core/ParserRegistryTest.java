package org.loginsight.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParserRegistryTest {
    private final ParserRegistry registry = new ParserRegistry(getClass().getClassLoader());

    public static class FixtureParser implements LogParser {
        public String format() {
            return "fixture";
        }

        public ParsedEvent parse(String line) throws ParseException {
            if (line.isBlank()) {
                throw new ParseException("empty fixture line");
            }
            return new ParsedEvent(Instant.EPOCH, "fixture-id", "test", "INFO", line);
        }
    }

    public static class DuplicateOne extends FixtureParser {
        @Override
        public String format() {
            return "collision";
        }
    }

    public static class DuplicateTwo extends DuplicateOne {
    }

    public static class NullFormat extends FixtureParser {
        @Override
        public String format() {
            return null;
        }
    }

    public static class BlankFormat extends FixtureParser {
        @Override
        public String format() {
            return " \t ";
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {NullFormat.class, BlankFormat.class})
    void rejectsMissingFormatDuringDiscovery(Class<?> provider, @TempDir Path root) throws IOException {
        Path services = Files.createDirectories(root.resolve("META-INF/services"));
        Files.writeString(services.resolve(LogParser.class.getName()), provider.getName());
        try (var loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, getClass().getClassLoader())) {
            var error = assertThrows(IllegalStateException.class, () -> new ParserRegistry(loader));
            assertEquals("Missing format on " + provider.getName(), error.getMessage());
        }
    }

    @Test
    void discoversRegisteredProviderAndReturnsItsFields() throws ParseException {
        LogParser parser = registry.select("fixture");
        assertInstanceOf(FixtureParser.class, parser);
        assertEquals(new ParsedEvent(Instant.EPOCH, "fixture-id", "test", "INFO", "event 42"),
                parser.parse("event 42"));
    }

    @Test
    void reportsMissingFormatByName() {
        var error = assertThrows(IllegalArgumentException.class, () -> registry.select("jsonl"));
        assertEquals("No parser for format: jsonl", error.getMessage());
    }

    @Test
    void rejectsAmbiguousRegistration() {
        var error = assertThrows(IllegalStateException.class, () -> registry.select("collision"));
        assertEquals("Multiple parsers for format: collision", error.getMessage());
    }

    @Test
    void distinguishesMalformedLineFromMissingProvider() {
        LogParser parser = registry.select("fixture");
        var error = assertThrows(ParseException.class, () -> parser.parse(" "));
        assertEquals("empty fixture line", error.getMessage());
    }

    @Test
    void rejectsBlankRequestId() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ParsedEvent(Instant.EPOCH, " ", "test", "INFO", "event"));
        assertEquals("requestId is required", error.getMessage());
    }
}
