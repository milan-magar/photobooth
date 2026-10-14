package com.photobooth;

import java.awt.image.BufferedImage;

public interface FrameSource {

    void start();

    void stop();

    BufferedImage getLatestFrame();

    String getDescription();
}