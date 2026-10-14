package com.photobooth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class HttpFrameSource implements FrameSource {

    /** Must match the endpoint your phone app POSTs to. */
    public static final String UPLOAD_ENDPOINT = "/upload";

    /** Endpoint for consumers (e.g. Python detector) to pull the newest frame. */
    public static final String LATEST_ENDPOINT = "/latest";

    /** Same limit as your existing StreamServer. */
    public static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    public static final int PORT = 8080;

    private HttpServer server;

    private volatile BufferedImage latest;         // decoded frame for the UI
    private volatile byte[] latestJpeg;            // raw bytes for /latest
    private volatile long lastFrameTime = 0L;

    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong lastLogTime  = new AtomicLong(System.currentTimeMillis());

    @Override
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(UPLOAD_ENDPOINT, new UploadHandler());
            server.createContext(LATEST_ENDPOINT, new LatestHandler());
            server.createContext("/", new RootHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("[PhotoBooth] Listening on port " + PORT);
            System.out.println("[PhotoBooth] Phone should POST JPEG frames to:  "
                + UPLOAD_ENDPOINT);
            System.out.println("[PhotoBooth] Latest frame available at:  GET "
                + LATEST_ENDPOINT);
        } catch (IOException e) {
            System.err.println("[PhotoBooth] Could not start HTTP server on port " + PORT);
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public BufferedImage getLatestFrame() {
        return latest;
    }

    @Override
    public String getDescription() {
        return "Phone (port " + PORT + ")";
    }

    public long getMillisSinceLastFrame() {
        return lastFrameTime == 0 ? Long.MAX_VALUE
                                  : System.currentTimeMillis() - lastFrameTime;
    }

    public long getReceivedCount() { return receivedCount.get(); }
    public long getRejectedCount() { return rejectedCount.get(); }

    // =================================================================
    //  /upload  — receives raw JPEG bytes from the phone
    // =================================================================
    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String remote = ex.getRemoteAddress().getAddress().getHostAddress();
            String ct     = ex.getRequestHeaders().getFirst("Content-Type");
            String lenHdr = ex.getRequestHeaders().getFirst("Content-Length");

            if (!"POST".equalsIgnoreCase(method)) {
                rejectedCount.incrementAndGet();
                sendText(ex, 405, "Method Not Allowed");
                return;
            }

            // ---- Read the whole body, enforcing MAX_FRAME_BYTES ----
            byte[] data;
            try (InputStream is = ex.getRequestBody();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = is.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FRAME_BYTES) {
                        rejectedCount.incrementAndGet();
                        System.out.println("[PhotoBooth] " + remote
                            + " -> 413 payload too large (" + total + " bytes)");
                        sendText(ex, 413, "Payload Too Large");
                        return;
                    }
                    baos.write(buffer, 0, read);
                }
                data = baos.toByteArray();
            }

            if (data.length == 0) {
                rejectedCount.incrementAndGet();
                System.out.println("[PhotoBooth] " + remote + " -> 400 empty body");
                sendText(ex, 400, "Empty body");
                return;
            }

            // ---- Decode the JPEG ----
            BufferedImage image = null;
            try {
                image = ImageIO.read(new ByteArrayInputStream(data));
            } catch (Exception ignored) {}

            if (image == null) {
                rejectedCount.incrementAndGet();
                System.out.println("[PhotoBooth] " + remote
                    + " -> 400 invalid JPEG (first bytes: "
                    + hexPreview(data, 16) + ")");
                sendText(ex, 400, "Invalid JPEG");
                return;
            }

            // ---- Convert to TYPE_INT_RGB for FilterEngine ----
            BufferedImage copy = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            copy.getGraphics().drawImage(image, 0, 0, null);

            // ---- Publish ----
            latest = copy;
            latestJpeg = data;
            lastFrameTime = System.currentTimeMillis();
            long count = receivedCount.incrementAndGet();

            // ---- Rate-limited logging (once per second) ----
            long now = System.currentTimeMillis();
            long last = lastLogTime.get();
            if (now - last > 1000) {
                if (lastLogTime.compareAndSet(last, now)) {
                    System.out.printf(
                        "[PhotoBooth] Frame #%d from %s (%d bytes, %dx%d, ct=%s, len=%s)%n",
                        count, remote, data.length,
                        image.getWidth(), image.getHeight(), ct, lenHdr);
                }
            }

            sendText(ex, 200, "OK");
        }
    }

    // =================================================================
    //  /latest  — serves the most recent JPEG bytes as image/jpeg
    // =================================================================
    private class LatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            byte[] frame = latestJpeg;
            if (frame == null) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "image/jpeg");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, frame.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(frame);
            }
        }
    }

    // =================================================================
    //  /  — a tiny landing page (useful for browser sanity checks)
    // =================================================================
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String html =
                "<!doctype html><html><head><title>Photo Booth</title></head>" +
                "<body style='font-family:sans-serif;padding:2em'>" +
                "<h2>Photo Booth frame receiver</h2>" +
                "<p>POST a JPEG to <code>" + UPLOAD_ENDPOINT + "</code></p>" +
                "<p>GET the newest frame from <code>" + LATEST_ENDPOINT + "</code></p>" +
                "<p>Frames received: <b>" + receivedCount.get() + "</b></p>" +
                "</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    // =================================================================
    //  Helpers
    // =================================================================
    private static void sendText(HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String hexPreview(byte[] b, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, b.length); i++) {
            sb.append(String.format("%02X ", b[i]));
        }
        return sb.toString().trim();
    }
}