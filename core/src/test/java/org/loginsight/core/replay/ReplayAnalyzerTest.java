package org.loginsight.core.replay;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.loginsight.api.ParseException;
import org.loginsight.api.ParsedEvent;
import org.loginsight.core.text.TextLogParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayAnalyzerTest {
    private static final Instant START = Instant.parse("2026-05-12T08:00:00Z");

    @Test
    void parsesCampusSampleAndKeepsRequestsSeparate() throws ParseException {
        String[] lines = {
            "2026-05-12T08:00:00Z R-17 campus INFO 收到选课请求",
            "2026-05-12T08:00:01Z R-18 campus INFO 收到选课请求",
            "2026-05-12T08:00:02Z R-17 campus INFO 调用名额服务",
            "2026-05-12T08:00:03Z R-18 campus INFO 选课成功",
            "2026-05-12T08:00:05Z R-17 campus ERROR 名额服务调用失败"
        };
        TextLogParser parser = new TextLogParser();
        List<ReplayRecord> imported = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            ParsedEvent event = parser.parse(lines[index]);
            imported.add(ReplayRecord.from(event, "campus-1", "campus.txt", index + 1,
                    lines[index], 0));
        }
        ReplayAnalyzer analyzer = new ReplayAnalyzer(imported);

        assertEquals(List.of(new ErrorBucket(START, 1)), analyzer.errorBuckets());
        assertEquals(List.of("R-17"), analyzer.failedRequestIds(START));
        assertEquals(List.of(1, 3, 5), analyzer.timeline("R-17").stream()
                .map(ReplayRecord::lineNumber).toList());
        assertEquals(List.of(2, 4), analyzer.timeline("R-18").stream()
                .map(ReplayRecord::lineNumber).toList());
    }

    @Test
    void combinesSourcesAndOrdersEqualTimestampsByImportThenLine() {
        ReplayRecord laterImport = record(3, 1, 1, "R-17", "INFO", "插件后续事件", "quota.jsonl");
        ReplayRecord earlierImportSecondLine = record(3, 3, 0, "R-17", "INFO", "入口第二行", "campus.txt");
        ReplayRecord earlierImportFirstLine = record(3, 1, 0, "R-17", "INFO", "入口第一行", "campus.txt");
        ReplayAnalyzer analyzer = new ReplayAnalyzer(List.of(
                laterImport, earlierImportSecondLine, earlierImportFirstLine));

        assertEquals(List.of("入口第一行", "入口第二行", "插件后续事件"),
                analyzer.timeline("R-17").stream().map(ReplayRecord::message).toList());
        assertEquals("quota.jsonl", analyzer.timeline("R-17").get(2).sourceFile());
        assertEquals(1, analyzer.timeline("R-17").get(2).lineNumber());
    }

    @Test
    void countsErrorsAcrossSourcesAndDeduplicatesFailedRequests() {
        ReplayAnalyzer analyzer = new ReplayAnalyzer(List.of(
                record(4, 5, 0, "R-17", "ERROR", "入口调用失败", "campus.txt"),
                record(3, 2, 1, "R-17", "ERROR", "名额服务超时", "quota.jsonl"),
                record(1, 8, 1, "R-18", "INFO", "查询成功", "quota.jsonl")));

        assertEquals(List.of(new ErrorBucket(START, 2)), analyzer.errorBuckets());
        assertEquals(List.of("R-17"), analyzer.failedRequestIds(START));
    }

    @Test
    void emptyInputHasNoBucketsOrTimeline() {
        ReplayAnalyzer analyzer = new ReplayAnalyzer(List.of());

        assertEquals(List.of(), analyzer.errorBuckets());
        assertEquals(List.of(), analyzer.failedRequestIds(START));
        assertEquals(List.of(), analyzer.timeline("R-17"));
    }

    @Test
    void filtersErrorsByUtcMinuteAndRejectsBlankRequestId() {
        Instant nextMinute = Instant.parse("2026-05-12T08:01:00Z");
        ReplayAnalyzer analyzer = new ReplayAnalyzer(List.of(
                record(59, 1, 0, "R-17", "ERROR", "本分钟错误", "campus.txt"),
                new ReplayRecord(nextMinute, "R-18", "campus", "ERROR", "下一分钟错误",
                        "campus-2", "campus.txt", 1, "raw", 1)));

        assertEquals(List.of(new ErrorBucket(START, 1), new ErrorBucket(nextMinute, 1)),
                analyzer.errorBuckets());
        assertEquals(List.of("R-17"), analyzer.failedRequestIds(START));
        assertEquals(List.of("R-18"), analyzer.failedRequestIds(nextMinute));
        assertThrows(IllegalArgumentException.class, () -> analyzer.timeline(" "));
    }

    private static ReplayRecord record(long second, int lineNumber, long importOrder,
                                       String requestId, String level, String message,
                                       String sourceFile) {
        ParsedEvent event = new ParsedEvent(START.plusSeconds(second), requestId,
                sourceFile.equals("campus.txt") ? "campus" : "quota", level, message);
        return ReplayRecord.from(event, sourceFile + "-" + importOrder, sourceFile, lineNumber,
                "raw line " + lineNumber, importOrder);
    }
}
