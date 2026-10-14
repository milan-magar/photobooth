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

    public static final String UPLOAD_ENDPOINT = "/upload";
    public static final String LATEST_ENDPOINT = "/latest";
    public static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    public static final int PORT = 8080;

    private HttpServer server;

    private volatile BufferedImage latest;
    private volatile byte[] latestJpeg;
    private volatile long lastFrameTime = 0L;

    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong lastLogTime = new AtomicLong(System.currentTimeMillis());

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
            System.out.println("[PhotoBooth] Phone POST endpoint: " + UPLOAD_ENDPOINT);
        } catch (IOException e) {
            System.err.println("[PhotoBooth] Could not start HTTP server on port " + PORT);
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        if (server != null) { server.stop(0); server = null; }
    }

    @Override
    public BufferedImage getLatestFrame() { return latest; }

    @Override
    public String getDescription() { return "Phone (port " + PORT + ")"; }

    public long getMillisSinceLastFrame() {
        return lastFrameTime == 0 ? Long.MAX_VALUE
                                  : System.currentTimeMillis() - lastFrameTime;
    }

    public long getReceivedCount() { return receivedCount.get(); }

    private class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                rejectedCount.incrementAndGet();
                sendText(ex, 405, "Method Not Allowed");
                return;
            }
            byte[] data;
            try (InputStream is = ex.getRequestBody();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read; long total = 0;
                while ((read = is.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FRAME_BYTES) {
                        rejectedCount.incrementAndGet();
                        sendText(ex, 413, "Payload Too Large");
                        return;
                    }
                    baos.write(buffer, 0, read);
                }
                data = baos.toByteArray();
            }
            if (data.length == 0) {
                rejectedCount.incrementAndGet();
                sendText(ex, 400, "Empty body");
                return;
            }
            BufferedImage image = null;
            try { image = ImageIO.read(new ByteArrayInputStream(data)); }
            catch (Exception ignored) {}
            if (image == null) {
                rejectedCount.incrementAndGet();
                sendText(ex, 400, "Invalid JPEG");
                return;
            }
            BufferedImage copy = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            copy.getGraphics().drawImage(image, 0, 0, null);

            latest = copy;
            latestJpeg = data;
            lastFrameTime = System.currentTimeMillis();
            long count = receivedCount.incrementAndGet();

            long now = System.currentTimeMillis();
            long last = lastLogTime.get();
            if (now - last > 1000 && lastLogTime.compareAndSet(last, now)) {
                System.out.printf("[PhotoBooth] Frame #%d (%d bytes, %dx%d)%n",
                    count, data.length, image.getWidth(), image.getHeight());
            }
            sendText(ex, 200, "OK");
        }
    }

    private class LatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            byte[] frame = latestJpeg;
            if (frame == null) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
            ex.getResponseHeaders().set("Content-Type", "image/jpeg");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, frame.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(frame); }
        }
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String html =
                "<!doctype html><html><head><title>Photo Booth</title></head>" +
                "<body style='font-family:sans-serif;padding:2em'>" +
                "<h2>Photo Booth frame receiver</h2>" +
                "<p>POST a JPEG to <code>" + UPLOAD_ENDPOINT + "</code></p>" +
                "<p>GET newest frame from <code>" + LATEST_ENDPOINT + "</code></p>" +
                "<p>Frames received: <b>" + receivedCount.get() + "</b></p>" +
                "</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
    }

    private static void sendText(HttpExchange ex, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}