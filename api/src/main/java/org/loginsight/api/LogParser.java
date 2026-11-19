package org.loginsight.api;

/** A service provider for one case-sensitive input format. */
public interface LogParser {
    String format();

    /** Parses one decoded line without the line terminator. */
    ParsedEvent parse(String line) throws ParseException;
}
