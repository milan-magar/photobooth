package com.photobooth;

import javax.swing.*;
import java.awt.*;

public class ShutterButton extends JButton {

    public ShutterButton() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int cx = w / 2, cy = h / 2;
        int r = Math.min(w, h) / 2 - 2;

        g2.setPaint(new GradientPaint(0, 0, new Color(240, 240, 240),
                                     0, h, new Color(150, 150, 150)));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        int ir = r - 6;
        g2.setPaint(new GradientPaint(0, cy - ir,
                                     new Color(230, 60, 60),
                                     0, cy + ir,
                                     new Color(150, 10, 10)));
        g2.fillOval(cx - ir, cy - ir, ir * 2, ir * 2);

        g2.setColor(new Color(255, 255, 255, 120));
        g2.fillOval(cx - ir + 4, cy - ir + 3, ir - 8, ir / 2);

        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(48, 48);
    }
}