package com.photobooth;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import javax.imageio.ImageIO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShutterBar extends JPanel {

    private final EffectsGridPanel effectsGrid;
    private final FilmStripPanel filmStrip;

    private JPanel dotsPanel;

    public ShutterBar(EffectsGridPanel effectsGrid, FilmStripPanel filmStrip) {
        this.effectsGrid = effectsGrid;
        this.filmStrip = filmStrip;

        setLayout(new BorderLayout());
        setBackground(new Color(60, 60, 60));
        setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        setPreferredSize(new Dimension(0, 52));

        // ---- Left: caption ----
        JLabel caption = new JLabel("Exploring filters and effects \u203A");
        caption.setFont(new Font("Helvetica", Font.PLAIN, 12));
        caption.setForeground(new Color(220, 220, 220));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(caption);

        // ---- Center: dots + shutter ----
        dotsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        dotsPanel.setOpaque(false);
        rebuildDots();

        ShutterButton shutter = new ShutterButton();
        shutter.setPreferredSize(new Dimension(36, 36));
        shutter.setToolTipText("Take photo");
        shutter.addActionListener(e -> triggerCapture());

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        center.setOpaque(false);
        center.add(dotsPanel);
        center.add(shutter);

        // ---- Right: Effects button ----
        JButton effectsBtn = new JButton("Effects");
        effectsBtn.setFont(new Font("Helvetica", Font.PLAIN, 12));
        effectsBtn.setForeground(Color.WHITE);
        effectsBtn.setBackground(new Color(80, 80, 80));
        effectsBtn.setFocusPainted(false);
        effectsBtn.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        effectsBtn.setOpaque(true);
        effectsBtn.setContentAreaFilled(true);
        effectsBtn.addActionListener(e -> {
            int next = (effectsGrid.getCurrentPage() + 1) % effectsGrid.getPageCount();
            effectsGrid.setPage(next);
            rebuildDots();
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(effectsBtn);

        add(left, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    private void rebuildDots() {
        dotsPanel.removeAll();
        int count = effectsGrid.getPageCount();
        int current = effectsGrid.getCurrentPage();
        for (int i = 0; i < count; i++) {
            final int page = i;
            JLabel dot = new JLabel("\u25CF") {
                @Override public Dimension getPreferredSize() { return new Dimension(14, 14); }
            };
            dot.setFont(new Font("SansSerif", Font.PLAIN, 12));
            dot.setForeground(i == current ? new Color(100, 180, 255)
                                            : new Color(120, 120, 120));
            dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dot.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    effectsGrid.setPage(page);
                    rebuildDots();
                }
            });
            dotsPanel.add(dot);
        }
        dotsPanel.revalidate();
        dotsPanel.repaint();
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

            // Save
            try {
                File dir = new File("captures");
                if (!dir.exists()) dir.mkdirs();
                String name = "capture_"
                    + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    + ".png";
                ImageIO.write(captured, "png", new File(dir, name));
            } catch (Exception e) { e.printStackTrace(); }

            // Polaroid slide-down, then add to filmstrip
            SwingUtilities.invokeLater(() -> {
                effectsGrid.startPolaroidAnimation(captured);
            });
        }).start();
    }

    public void registerPolaroidCallback() {
        effectsGrid.setOnCaptureFinished(img -> {
            SwingUtilities.invokeLater(() -> filmStrip.addThumbnail(img));
        });
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