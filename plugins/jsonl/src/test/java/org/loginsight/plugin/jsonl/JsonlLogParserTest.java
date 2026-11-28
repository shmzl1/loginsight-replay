package org.loginsight.plugin.jsonl;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.loginsight.api.LogParser;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlLogParserTest {
    private static final String VALID_LINE = "{\"timestamp\":\"2026-05-12T08:00:04Z\","
            + "\"requestId\":\"R-17\",\"service\":\"quota\",\"level\":\"ERROR\","
            + "\"message\":\"获取连接超时\"}";

    private final LogParser parser = new JsonlLogParser();

    @Test
    void formatIdentifiesJsonl() {
        assertEquals("jsonl", parser.format());
    }

    @Test
    void mapsValidObjectToEvent() throws ParseException {
        ParsedEvent event = parser.parse(VALID_LINE);

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
    void rejectsTrailingContentAfterObject() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse(VALID_LINE + " junk"));

        assertEquals("trailing content after JSON object", error.getMessage());
    }

    @Test
    void rejectsTrailingJsonValue() {
        ParseException error = assertThrows(ParseException.class,
                () -> parser.parse(VALID_LINE + "{\"requestId\":\"R-18\"}"));

        assertTrue(error.getMessage().startsWith("trailing content"));
    }

    @Test
    void reportsFieldNameForNonStringValue() {
        String line = "{\"timestamp\":\"2026-05-12T08:00:04Z\",\"requestId\":17,"
                + "\"service\":\"quota\",\"level\":\"ERROR\",\"message\":\"获取连接超时\"}";

        ParseException error = assertThrows(ParseException.class, () -> parser.parse(line));
        assertEquals("field is not a string: requestId", error.getMessage());
    }

    @Test
    void reportsFieldNameForNonStringTimestamp() {
        String line = "{\"timestamp\":1750000000,\"requestId\":\"R-17\","
                + "\"service\":\"quota\",\"level\":\"ERROR\",\"message\":\"获取连接超时\"}";

        ParseException error = assertThrows(ParseException.class, () -> parser.parse(line));
        assertEquals("field is not a string: timestamp", error.getMessage());
    }

    @Test
    void discoversParserThroughServiceLoader() {
        LogParser loaded = ServiceLoader.load(LogParser.class, getClass().getClassLoader())
                .findFirst().orElseThrow();

        assertInstanceOf(JsonlLogParser.class, loaded);
        assertEquals("jsonl", loaded.format());
    }

    /** The build must hand over the real packaged JAR; a missing path fails instead of passing. */
    @Test
    void discoversParserFromPackagedJar() throws Exception {
        Path jar = packagedPluginJar();
        try (URLClassLoader loader = isolatedLoader(jar)) {
            Class<?> serviceType = loader.loadClass(LogParser.class.getName());
            ServiceLoader<?> services = loadServices(serviceType, loader);
            Iterator<?> providers = services.iterator();
            assertTrue(providers.hasNext(), "the packaged JAR must declare a parser");
            Object provider = providers.next();
            assertFalse(providers.hasNext(), "the packaged JAR must declare exactly one parser");
            assertEquals(JsonlLogParser.class.getName(), provider.getClass().getName());
            assertEquals(jar, codeSourceOf(provider));
            assertEquals("jsonl", provider.getClass().getMethod("format").invoke(provider));
            Object event = provider.getClass().getMethod("parse", String.class).invoke(provider, VALID_LINE);
            assertEquals("获取连接超时", event.getClass().getMethod("message").invoke(event));
        }
    }

    /** Visible from the JAR, the API and the JSON library only, never from the test class path. */
    private static URLClassLoader isolatedLoader(Path jar) throws Exception {
        URL apiClasses = LogParser.class.getProtectionDomain().getCodeSource().getLocation();
        URL jsonJar = JSONObject.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new URL[] {jar.toUri().toURL(), apiClasses, jsonJar},
                ClassLoader.getPlatformClassLoader());
    }

    private static ServiceLoader<?> loadServices(Class<?> serviceType, ClassLoader loader)
            throws ReflectiveOperationException {
        return (ServiceLoader<?>) ServiceLoader.class
                .getMethod("load", Class.class, ClassLoader.class)
                .invoke(null, serviceType, loader);
    }

    private static Path packagedPluginJar() {
        String path = System.getProperty("plugin.jar");
        assertTrue(path != null && !path.isBlank(), "plugin.jar must be set by the Maven build");
        Path jar = Path.of(path).toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "packaged plugin JAR must exist: " + jar);
        return jar;
    }

    private static Path codeSourceOf(Object provider) throws URISyntaxException {
        URL location = provider.getClass().getProtectionDomain().getCodeSource().getLocation();
        return Path.of(location.toURI()).toAbsolutePath();
    }
}
