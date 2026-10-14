package com.photobooth;

import javax.swing.*;
import java.awt.image.BufferedImage;

public class CaptureWorker extends SwingWorker<Void, BufferedImage> {

    private final FrameSource source;
    private final EffectsGridPanel target;
    private volatile boolean running = true;

    public CaptureWorker(FrameSource source, EffectsGridPanel target) {
        this.source = source;
        this.target = target;
    }

    @Override
    protected Void doInBackground() {
        final int TARGET_FPS = 30;
        final long FRAME_DELAY_MS = 1000L / TARGET_FPS;

        while (running && !isCancelled()) {
            long start = System.currentTimeMillis();

            BufferedImage frame = source.getLatestFrame();
            if (frame != null) {
                publish(frame);
            }

            long elapsed = System.currentTimeMillis() - start;
            long sleep = FRAME_DELAY_MS - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
            }
        }
        return null;
    }

    @Override
    protected void process(java.util.List<BufferedImage> chunks) {
        if (!chunks.isEmpty()) {
            target.updateFrame(chunks.get(chunks.size() - 1));
        }
    }

    public void stop() {
        running = false;
        cancel(true);
    }
}