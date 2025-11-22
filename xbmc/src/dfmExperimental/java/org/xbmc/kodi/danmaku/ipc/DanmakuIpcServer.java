package org.xbmc.kodi.danmaku.ipc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.xbmc.kodi.XBMCURIUtils;
import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.DanmakuService;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Minimal loopback HTTP server that allows Python plugins to push danmaku payloads.
 */
public final class DanmakuIpcServer implements Closeable {
    private static final String TAG = "DanmakuIpcServer";
    private static final int DEFAULT_PORT = 11235;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final String TOKEN_SPECIAL_PATH =
            "special://profile/addon_data/script.service.dandanplay/ipc_token.json";

    private final Context context;
    private final DanmakuEngine engine;
    private final BiliXmlParser parser;
    private final Handler mainHandler;
    private final ExecutorService requestExecutor;
    private final Gson gson = new Gson();
    private final XBMCURIUtils uriUtils = new XBMCURIUtils();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;
    private String token;
    private int port;

    public DanmakuIpcServer(Context context,
                            DanmakuEngine engine,
                            BiliXmlParser parser,
                            Handler mainHandler) {
        this.context = context.getApplicationContext();
        this.engine = engine;
        this.parser = parser;
        this.mainHandler = mainHandler == null ? new Handler(Looper.getMainLooper()) : mainHandler;
        this.requestExecutor = Executors.newCachedThreadPool();
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            this.serverSocket = createServerSocket(loopback);
            this.port = serverSocket.getLocalPort();
            this.token = UUID.randomUUID().toString().replace("-", "");
            this.running = true;
            this.acceptThread = new Thread(this::acceptLoop, "DanmakuIpcServer");
            acceptThread.start();
            writeTokenFile();
            Log.i(TAG, "Danmaku IPC server listening on 127.0.0.1:" + port);
        } catch (IOException ex) {
            Log.w(TAG, "Unable to start danmaku IPC server", ex);
            closeQuietly(serverSocket);
            running = false;
        }
    }

    private ServerSocket createServerSocket(InetAddress loopback) throws IOException {
        try {
            ServerSocket socket = new ServerSocket(DEFAULT_PORT, 50, loopback);
            socket.setReuseAddress(true);
            return socket;
        } catch (IOException ex) {
            ServerSocket fallback = new ServerSocket(0, 50, loopback);
            fallback.setReuseAddress(true);
            Log.w(TAG, "Default port busy; using " + fallback.getLocalPort());
            return fallback;
        }
    }

    private void acceptLoop() {
        while (running && serverSocket != null) {
            try {
                Socket socket = serverSocket.accept();
                requestExecutor.execute(() -> handleClient(socket));
            } catch (IOException ex) {
                if (running) {
                    Log.w(TAG, "IPC accept loop terminated", ex);
                }
                break;
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket client = socket;
             BufferedInputStream in = new BufferedInputStream(client.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream())) {
            HttpRequest request = HttpRequest.parse(in);
            if (request == null) {
                return;
            }
            if (!isAuthorized(request.headers.get("authorization"))) {
                sendResponse(out, 401, "Unauthorized", jsonMessage("error", "unauthorized"));
                return;
            }
            dispatch(request, out);
        } catch (IOException ex) {
            Log.w(TAG, "IPC client error", ex);
        }
    }

    private boolean isAuthorized(String authHeader) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (authHeader == null) {
            return false;
        }
        return authHeader.trim().equals("Bearer " + token);
    }

    private void dispatch(HttpRequest request, OutputStream out) throws IOException {
        if ("/v1/danmaku/load".equals(request.path) && "POST".equals(request.method)) {
            handleLoad(request, out);
            return;
        }
        if ("/v1/danmaku/control".equals(request.path) && "POST".equals(request.method)) {
            handleControl(request, out);
            return;
        }
        if ("/v1/danmaku/unload".equals(request.path) && "POST".equals(request.method)) {
            handleUnload(request, out);
            return;
        }
        if ("/v1/danmaku/status".equals(request.path) && "GET".equals(request.method)) {
            handleStatus(out);
            return;
        }
        sendResponse(out, 404, "Not Found", jsonMessage("error", "unknown_path"));
    }

    private void handleLoad(HttpRequest request, OutputStream out) throws IOException {
        if (request.body.length == 0) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "empty_body"));
            return;
        }
        JsonObject body = JsonParser.parseString(new String(request.body, StandardCharsets.UTF_8)).getAsJsonObject();
        String mediaKeyRaw = optString(body, "mediaKey");
        if (mediaKeyRaw == null || mediaKeyRaw.isEmpty()) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "mediaKey_required"));
            return;
        }
        MediaKey mediaKey;
        try {
            mediaKey = MediaKey.deserialize(mediaKeyRaw);
        } catch (IllegalArgumentException ex) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "mediaKey_invalid"));
            return;
        }
        String trackId = optString(body, "trackId");
        if (trackId == null || trackId.isEmpty()) {
            trackId = "ipc-" + UUID.randomUUID();
        }
        String title = optString(body, "title");
        if (title == null || title.isEmpty()) {
            title = "Remote Danmaku";
        }
        DanmakuConfig config = parseConfig(body.getAsJsonObject("prefs"));
        try {
            List<DanmakuItem> items = parseItems(body);
            DanmakuTrack track = new DanmakuTrack(
                    trackId,
                    title,
                    DanmakuTrack.SourceType.REMOTE,
                    optString(body, "payloadPath", trackId),
                    config,
                    mediaKey);
            List<DanmakuItem> safeItems = Collections.unmodifiableList(new ArrayList<>(items));
            postToMain(() -> {
                engine.bindTrack(track, safeItems, config);
                engine.setVisibility(true);
                return null;
            });
            JsonObject response = new JsonObject();
            response.addProperty("ok", true);
            response.addProperty("trackId", trackId);
            response.addProperty("items", safeItems.size());
            sendResponse(out, 200, "OK", response.toString());
        } catch (PayloadException ex) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", ex.getMessage()));
        } catch (Exception ex) {
            Log.w(TAG, "Failed to bind danmaku payload", ex);
            sendResponse(out, 500, "Server Error", jsonMessage("error", "internal_error"));
        }
    }

    private void handleControl(HttpRequest request, OutputStream out) throws IOException {
        if (request.body.length == 0) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "empty_body"));
            return;
        }
        JsonObject body = JsonParser.parseString(new String(request.body, StandardCharsets.UTF_8)).getAsJsonObject();
        String action = optString(body, "action");
        if (action == null) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "action_required"));
            return;
        }
        JsonObject response = new JsonObject();
        try {
            switch (action) {
                case "pause":
                    postToMain(() -> {
                        engine.pause();
                        return null;
                    });
                    break;
                case "resume": {
                    long pos = optLong(body, "positionMs", engine.getStatus().getPositionMs());
                    float speed = optFloat(body, "speed", engine.getStatus().getSpeed());
                    postToMain(() -> {
                        engine.updatePlaybackState(pos, speed, true);
                        return null;
                    });
                    break;
                }
                case "seek": {
                    long pos = optLong(body, "positionMs", -1L);
                    if (pos < 0) {
                        throw new PayloadException("position_required");
                    }
                    postToMain(() -> {
                        engine.seek(pos);
                        return null;
                    });
                    break;
                }
                case "set_visibility": {
                    boolean visible = body.has("visible") && body.get("visible").getAsBoolean();
                    postToMain(() -> {
                        engine.setVisibility(visible);
                        return null;
                    });
                    break;
                }
                case "set_speed": {
                    float speed = optFloat(body, "speed", 1.0f);
                    postToMain(() -> {
                        engine.updateSpeed(speed);
                        return null;
                    });
                    break;
                }
                case "update_prefs": {
                    DanmakuConfig config = parseConfig(body.getAsJsonObject("prefs"));
                    postToMain(() -> {
                        engine.updateConfig(config);
                        return null;
                    });
                    break;
                }
                default:
                    throw new PayloadException("unsupported_action");
            }
            response.addProperty("ok", true);
            sendResponse(out, 200, "OK", response.toString());
        } catch (PayloadException ex) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", ex.getMessage()));
        } catch (Exception ex) {
            Log.w(TAG, "Control action failed", ex);
            sendResponse(out, 500, "Server Error", jsonMessage("error", "internal_error"));
        }
    }

    private void handleUnload(HttpRequest request, OutputStream out) throws IOException {
        JsonObject body = request.body.length == 0
                ? new JsonObject()
                : JsonParser.parseString(new String(request.body, StandardCharsets.UTF_8)).getAsJsonObject();
        String mediaKeyRaw = optString(body, "mediaKey");
        String trackId = optString(body, "trackId");
        try {
            MediaKey mediaKey = mediaKeyRaw == null ? null : MediaKey.deserialize(mediaKeyRaw);
            postToMain(() -> {
                if (mediaKey != null && trackId != null) {
                    engine.removeTrack(mediaKey, trackId);
                } else if (mediaKey != null) {
                    engine.removeTrack(mediaKey, engine.getActiveTrack() != null
                            && mediaKey.equals(engine.getActiveTrack().getMediaKey())
                            ? engine.getActiveTrack().getId()
                            : null);
                } else {
                    DanmakuTrack active = engine.getActiveTrack();
                    if (active != null) {
                        engine.removeTrack(active.getMediaKey(), active.getId());
                    }
                }
                engine.setVisibility(false);
                return null;
            });
            sendResponse(out, 200, "OK", jsonMessage("ok", true));
        } catch (Exception ex) {
            sendResponse(out, 400, "Bad Request", jsonMessage("error", "mediaKey_invalid"));
        }
    }

    private void handleStatus(OutputStream out) throws IOException {
        try {
            DanmakuService.DanmakuStatus status = postToMain(() -> engine.getStatus());
            JsonObject json = new JsonObject();
            json.addProperty("visible", status.isVisible());
            json.addProperty("playing", status.isPlaying());
            json.addProperty("positionMs", status.getPositionMs());
            json.addProperty("speed", status.getSpeed());
            sendResponse(out, 200, "OK", json.toString());
        } catch (Exception ex) {
            sendResponse(out, 500, "Server Error", jsonMessage("error", "internal_error"));
        }
    }

    private List<DanmakuItem> parseItems(JsonObject body) throws PayloadException {
        String format = optString(body, "format");
        if (format == null || format.isEmpty() || "bili-xml".equalsIgnoreCase(format)) {
            InputStream input = openPayloadStream(body);
            try (InputStream stream = input) {
                return parser.parse(stream);
            } catch (IOException ex) {
                throw new PayloadException("payload_parse_failed");
            }
        }
        throw new PayloadException("unsupported_format");
    }

    private InputStream openPayloadStream(JsonObject body) throws PayloadException {
        String payloadPath = optString(body, "payloadPath");
        if (payloadPath != null && !payloadPath.isEmpty()) {
            try {
                return new FileInputStream(payloadPath);
            } catch (IOException ex) {
                throw new PayloadException("payload_path_unreadable");
            }
        }
        String payload = optString(body, "payload");
        if (payload == null) {
            throw new PayloadException("payload_required");
        }
        String encoding = optString(body, "encoding");
        if (encoding == null) {
            encoding = "plain";
        }
        encoding = encoding.toLowerCase(Locale.US);
        byte[] bytes;
        switch (encoding) {
            case "plain":
                bytes = payload.getBytes(StandardCharsets.UTF_8);
                break;
            case "base64":
                bytes = Base64.decode(payload, Base64.DEFAULT);
                break;
            case "gzip+base64":
            case "base64+gzip":
                bytes = Base64.decode(payload, Base64.DEFAULT);
                bytes = decompress(bytes);
                break;
            case "gzip":
                bytes = decompress(payload.getBytes(StandardCharsets.UTF_8));
                break;
            default:
                throw new PayloadException("unsupported_encoding");
        }
        return new ByteArrayInputStream(bytes);
    }

    private byte[] decompress(byte[] data) throws PayloadException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = gzip.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new PayloadException("gzip_failed");
        }
    }

    private DanmakuConfig parseConfig(JsonObject prefs) {
        if (prefs == null) {
            return DanmakuConfig.defaults();
        }
        float textScale = optFloat(prefs, "textScaleSp", 1.0f);
        float speed = optFloat(prefs, "scrollSpeedFactor", 1.0f);
        float alpha = optFloat(prefs, "alpha", 1.0f);
        int maxOnScreen = optInt(prefs, "maxOnScreen", 0);
        int maxLines = optInt(prefs, "maxLines", 0);
        long offset = optLong(prefs, "offsetMs", 0L);
        List<String> keywords = new ArrayList<>();
        JsonArray keywordArray = prefs.getAsJsonArray("keywordFilter");
        if (keywordArray != null) {
            for (JsonElement element : keywordArray) {
                if (element != null && element.isJsonPrimitive()) {
                    String value = element.getAsString();
                    if (value != null && !value.isEmpty()) {
                        keywords.add(value);
                    }
                }
            }
        }
        JsonObject typeObj = prefs.getAsJsonObject("typeEnabled");
        DanmakuConfig.TypeEnabled typeEnabled = typeObj == null
                ? DanmakuConfig.TypeEnabled.allEnabled()
                : new DanmakuConfig.TypeEnabled(
                        typeObj.has("scroll") && typeObj.get("scroll").getAsBoolean(),
                        typeObj.has("top") && typeObj.get("top").getAsBoolean(),
                        typeObj.has("bottom") && typeObj.get("bottom").getAsBoolean(),
                        typeObj.has("positioned") && typeObj.get("positioned").getAsBoolean());
        return new DanmakuConfig(
                textScale,
                speed,
                alpha,
                maxOnScreen,
                maxLines,
                keywords,
                typeEnabled,
                offset
        );
    }

    private void writeTokenFile() {
        String resolved = uriUtils.substitutePath(TOKEN_SPECIAL_PATH);
        if (resolved == null) {
            Log.w(TAG, "Unable to resolve token path: " + TOKEN_SPECIAL_PATH);
            return;
        }
        File file = new File(resolved);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Unable to create token directory: " + parent);
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("port", port);
        json.addProperty("token", token);
        json.addProperty("issuedAt", System.currentTimeMillis());
        String payload = gson.toJson(json);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp, false)) {
            writer.write(payload);
            writer.flush();
            if (!tmp.renameTo(file)) {
                Log.w(TAG, "Unable to atomically update token file; falling back to direct write");
                try (FileWriter fallback = new FileWriter(file, false)) {
                    fallback.write(payload);
                }
            }
        } catch (IOException ex) {
            Log.w(TAG, "Unable to write token file", ex);
        }
    }

    private void sendResponse(OutputStream out, int code, String phrase, String body) throws IOException {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String status = "HTTP/1.1 " + code + " " + phrase + "\r\n";
        String headers =
                "Content-Type: application/json\r\n" +
                        "Content-Length: " + payload.length + "\r\n" +
                        "Connection: close\r\n\r\n";
        out.write(status.getBytes(StandardCharsets.US_ASCII));
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    private String jsonMessage(String key, Object value) {
        JsonObject json = new JsonObject();
        if (value instanceof Boolean) {
            json.addProperty(key, (Boolean) value);
        } else {
            json.addProperty(key, String.valueOf(value));
        }
        return json.toString();
    }

    private <T> T postToMain(CallableWithException<T> callable) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return callable.call();
        }
        FutureTask<T> task = new FutureTask<>(callable::call);
        mainHandler.post(task);
        return task.get();
    }

    @Override
    public synchronized void close() {
        running = false;
        closeQuietly(serverSocket);
        if (acceptThread != null) {
            try {
                acceptThread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            acceptThread = null;
        }
        requestExecutor.shutdownNow();
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static String optString(JsonObject obj, String key) {
        return optString(obj, key, null);
    }

    private static String optString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private static int optInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsInt();
    }

    private static long optLong(JsonObject obj, String key, long defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsLong();
    }

    private static float optFloat(JsonObject obj, String key, float defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsFloat();
    }

    private static final class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> headers;
        final byte[] body;

        private HttpRequest(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        static HttpRequest parse(InputStream in) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return null;
            }
            String method = parts[0];
            String path = parts[1];
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
                String value = line.substring(colon + 1).trim();
                headers.put(key, value);
            }
            int contentLength = 0;
            if (headers.containsKey("content-length")) {
                try {
                    contentLength = Integer.parseInt(headers.get("content-length"));
                } catch (NumberFormatException ignored) {
                    contentLength = 0;
                }
            }
            contentLength = Math.max(0, Math.min(contentLength, DanmakuIpcServer.MAX_BODY_BYTES));
            byte[] body = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int r = in.read(body, read, contentLength - read);
                if (r == -1) {
                    break;
                }
                read += r;
            }
            if (read < contentLength) {
                byte[] trimmed = new byte[read];
                System.arraycopy(body, 0, trimmed, 0, read);
                body = trimmed;
            }
            return new HttpRequest(method, path, headers, body);
        }
    }

    private interface CallableWithException<T> {
        T call() throws Exception;
    }

    private static final class PayloadException extends Exception {
        PayloadException(String message) {
            super(message);
        }
    }
}
