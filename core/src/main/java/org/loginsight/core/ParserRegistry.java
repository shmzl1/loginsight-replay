package org.loginsight.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.loginsight.api.LogParser;

/** Discovers classpath providers once. Selection never depends on discovery order. */
public final class ParserRegistry {
    private final Map<String, List<LogParser>> providers = new HashMap<>();

    public ParserRegistry(ClassLoader loader) {
        for (LogParser parser : ServiceLoader.load(LogParser.class, loader)) {
            String format = parser.format();
            if (format == null || format.isBlank()) {
                throw new IllegalStateException("Missing format on " + parser.getClass().getName());
            }
            providers.computeIfAbsent(format, key -> new ArrayList<>()).add(parser);
        }
    }

    public LogParser select(String format) {
        List<LogParser> matches = providers.get(format);
        if (matches == null) {
            throw new IllegalArgumentException("No parser for format: " + format);
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple parsers for format: " + format);
        }
        return matches.get(0);
    }
}
