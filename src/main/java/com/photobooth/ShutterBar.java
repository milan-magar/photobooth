package com.photobooth;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import javax.imageio.ImageIO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShutterBar extends JPanel {

    private final EffectsGridPanel effectsGrid;
    private final FilmStripPanel filmStrip;

    public ShutterBar(EffectsGridPanel effectsGrid, FilmStripPanel filmStrip) {
        this.effectsGrid = effectsGrid;
        this.filmStrip = filmStrip;

        setLayout(new BorderLayout());
        setBackground(new Color(210, 210, 210));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(140, 140, 140)),
            BorderFactory.createEmptyBorder(6, 20, 6, 20)));
        setPreferredSize(new Dimension(0, 56));

        JButton prev = arrowButton("\u25C0");
        prev.addActionListener(e -> { });

        ShutterButton shutter = new ShutterButton();
        shutter.setPreferredSize(new Dimension(48, 48));
        shutter.setToolTipText("Take photo");
        shutter.addActionListener(e -> triggerCapture());

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        center.setOpaque(false);
        center.add(shutter);

        JLabel effectsLbl = new JLabel("Effects");
        effectsLbl.setFont(new Font("Helvetica", Font.PLAIN, 12));
        effectsLbl.setForeground(new Color(60, 60, 60));

        JLabel pageLbl = new JLabel("1");
        pageLbl.setFont(new Font("Helvetica", Font.PLAIN, 11));
        pageLbl.setForeground(new Color(80, 80, 80));
        pageLbl.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(150, 150, 150)),
            BorderFactory.createEmptyBorder(1, 6, 1, 6)));

        JButton next = arrowButton("\u25B6");
        next.addActionListener(e -> { });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        right.add(effectsLbl);
        right.add(pageLbl);
        right.add(next);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(prev);

        add(left, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    private JButton arrowButton(String glyph) {
        JButton b = new JButton(glyph);
        b.setFont(new Font("Helvetica", Font.BOLD, 12));
        b.setForeground(new Color(60, 60, 60));
        b.setBackground(new Color(210, 210, 210));
        b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        return b;
    }

    private void triggerCapture() {
        if (effectsGrid.captureSelected() == null) return;

        new Thread(() -> {
            for (int i = 3; i >= 1; i--) {
                final int n = i;
                SwingUtilities.invokeLater(() -> effectsGrid.showCountdown(n));
                sleep(700);
            }
            SwingUtilities.invokeLater(effectsGrid::clearCountdown);
            SwingUtilities.invokeLater(effectsGrid::showFlash);
            sleep(150);
            SwingUtilities.invokeLater(effectsGrid::hideFlash);

            playShutterSound();

            BufferedImage captured = effectsGrid.captureSelected();
            if (captured == null) return;

            try {
                File dir = new File("captures");
                if (!dir.exists()) dir.mkdirs();
                String name = "capture_"
                    + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".png";
                ImageIO.write(captured, "png", new File(dir, name));
            } catch (Exception e) {
                e.printStackTrace();
            }

            SwingUtilities.invokeLater(() -> filmStrip.addThumbnail(captured));
        }).start();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void playShutterSound() {
        try {
            URL url = getClass().getResource("/shutter.wav");
            if (url == null) return;
            AudioInputStream ais = AudioSystem.getAudioInputStream(url);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (Exception ignored) {}
    }
}