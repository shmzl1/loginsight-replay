package org.loginsight.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.loginsight.api.ParseException;
import org.loginsight.core.ParserRegistry;
import org.loginsight.core.replay.ReplayAnalyzer;
import org.loginsight.core.replay.ReplayRecord;

/** Local HTTP entry point. Imports live in memory until the process exits. */
public final class ReplayServer {
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private final ParserRegistry parsers;
    private final List<ReplayRecord> records = new ArrayList<>();
    private long importOrder;

    private ReplayServer(ClassLoader loader) {
        parsers = new ParserRegistry(loader);
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: ReplayServer [port [plugin.jar]]");
        }
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        URL[] plugins = new URL[0];
        if (args.length == 2) {
            Path jar = Path.of(args[1]);
            if (!Files.isRegularFile(jar)) {
                throw new IllegalArgumentException("Plugin JAR not found: " + jar);
            }
            plugins = new URL[]{jar.toUri().toURL()};
        }
        var loader = new URLClassLoader(plugins, ReplayServer.class.getClassLoader());
        var replay = new ReplayServer(loader);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", replay::handle);
        server.start();
        System.out.println("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; object-src 'none'; frame-ancestors 'none'");
            String host = "127.0.0.1:" + exchange.getLocalAddress().getPort();
            if (!host.equals(exchange.getRequestHeaders().getFirst("Host"))) {
                error(exchange, 403, "请通过启动器显示的本地地址访问");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                api(exchange, path);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                error(exchange, 405, "请求方法不受支持");
                return;
            }
            switch (path) {
                case "/" -> asset(exchange, "index.html", "text/html");
                case "/app.css" -> asset(exchange, "app.css", "text/css");
                case "/app.js" -> asset(exchange, "app.js", "text/javascript");
                default -> error(exchange, 404, "页面不存在");
            }
        }
    }

    private synchronized void api(HttpExchange exchange, String path) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            error(exchange, 405, "请求方法不受支持");
            return;
        }
        if (!"application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            error(exchange, 415, "请求必须使用 JSON");
            return;
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) {
            error(exchange, 413, "导入请求超过 8 MiB");
            return;
        }
        try {
            String body = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            var data = new JSONObject(body);
            JSONObject result = switch (path) {
                case "/api/import" -> importFile(data);
                case "/api/buckets" -> buckets();
                case "/api/requests" -> new JSONObject().put("failedRequestIds",
                        new JSONArray(new ReplayAnalyzer(records).failedRequestIds(
                                Instant.parse(data.getString("minuteStart")))));
                case "/api/timeline" -> timeline(data.getString("requestId"));
                default -> null;
            };
            if (result == null) {
                error(exchange, 404, "接口不存在");
                return;
            }
            json(exchange, 200, result);
        } catch (JSONException | DateTimeParseException | java.nio.charset.CharacterCodingException ex) {
            error(exchange, 400, "请求数据格式不正确，请使用 UTF-8 JSON");
        } catch (IllegalArgumentException ex) {
            error(exchange, 400, ex.getMessage());
        }
    }

    private JSONObject importFile(JSONObject data) {
        String file = data.getString("file");
        String content = data.getString("content");
        if (file.isBlank() || content.isEmpty()) {
            throw new IllegalArgumentException("请选择非空日志文件");
        }
        var parser = parsers.select(data.getString("format"));
        List<ReplayRecord> imported = new ArrayList<>();
        String sourceId = Long.toString(importOrder + 1);
        int line = 0;
        for (String raw : content.lines().toList()) {
            line++;
            try {
                imported.add(ReplayRecord.from(parser.parse(raw), sourceId, file, line, raw, importOrder));
            } catch (ParseException | IllegalArgumentException ex) {
                throw new IllegalArgumentException(file + " 第 " + line + " 行：" + ex.getMessage(), ex);
            }
        }
        records.addAll(imported);
        importOrder++;
        return buckets().put("imported", imported.size());
    }

    private JSONObject buckets() {
        var buckets = new JSONArray();
        new ReplayAnalyzer(records).errorBuckets().forEach(bucket -> buckets.put(new JSONObject()
                .put("minuteStart", bucket.minuteStart().toString()).put("errorCount", bucket.errorCount())));
        return new JSONObject().put("errorBuckets", buckets);
    }

    private JSONObject timeline(String requestId) {
        var events = new JSONArray();
        for (ReplayRecord event : new ReplayAnalyzer(records).timeline(requestId)) {
            events.put(new JSONObject().put("timestamp", event.timestamp().toString())
                    .put("requestId", event.requestId()).put("service", event.service())
                    .put("level", event.level()).put("message", event.message())
                    .put("sourceId", event.sourceId()).put("sourceFile", event.sourceFile())
                    .put("lineNumber", event.lineNumber()).put("rawLine", event.rawLine()));
        }
        return new JSONObject().put("timeline", events);
    }

    private static void asset(HttpExchange exchange, String file, String type) throws IOException {
        try (var input = ReplayServer.class.getResourceAsStream("/web/" + file)) {
            if (input == null) {
                error(exchange, 404, "页面资源不存在");
                return;
            }
            send(exchange, 200, type, input.readAllBytes());
        }
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, new JSONObject().put("error", message));
    }

    private static void json(HttpExchange exchange, int status, JSONObject data) throws IOException {
        send(exchange, status, "application/json", data.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
