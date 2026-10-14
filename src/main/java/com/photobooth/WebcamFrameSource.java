package com.photobooth;

import com.github.sarxos.webcam.Webcam;

import java.awt.image.BufferedImage;

public class WebcamFrameSource implements FrameSource {

    private final Webcam webcam;
    private volatile BufferedImage latest;

    public WebcamFrameSource(Webcam webcam) {
        this.webcam = webcam;
    }

    @Override
    public void start() {
        if (webcam != null && !webcam.isOpen()) {
            webcam.open();
        }
    }

    @Override
    public void stop() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }

    @Override
    public BufferedImage getLatestFrame() {
        if (webcam == null || !webcam.isOpen()) return latest;
        BufferedImage raw = webcam.getImage();
        if (raw != null) {
            BufferedImage copy = new BufferedImage(
                raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_RGB);
            copy.getGraphics().drawImage(raw, 0, 0, null);
            latest = copy;
        }
        return latest;
    }

    @Override
    public String getDescription() {
        return "Webcam";
    }
}