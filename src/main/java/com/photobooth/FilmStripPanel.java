package com.photobooth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class FilmStripPanel extends JPanel {

    private static final int THUMB_W = 68;
    private static final int THUMB_H = 52;
    private static final int PAPER   = 3;

    private final List<BufferedImage> photos = new ArrayList<>();
    private final JPanel strip;

    public FilmStripPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(180, 180, 180));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(140, 140, 140)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        setPreferredSize(new Dimension(0, THUMB_H + 24));

        strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        strip.setOpaque(false);

        JScrollPane scroll = new JScrollPane(strip);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);

        add(scroll, BorderLayout.CENTER);
    }

    public void addThumbnail(BufferedImage full) {
        photos.add(full);

        BufferedImage thumb = new BufferedImage(THUMB_W, THUMB_H,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(full, 0, 0, THUMB_W, THUMB_H, null);
        g.dispose();

        JPanel holder = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.drawImage(thumb, PAPER, PAPER, THUMB_W, THUMB_H, null);
            }
            @Override public Dimension getPreferredSize() {
                return new Dimension(THUMB_W + PAPER * 2, THUMB_H + PAPER * 2);
            }
        };
        holder.setOpaque(false);
        holder.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        holder.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showFull(full); }
        });

        strip.add(holder);
        strip.revalidate();
        strip.repaint();

        SwingUtilities.invokeLater(() -> {
            JScrollPane sp = (JScrollPane) strip.getParent().getParent();
            JScrollBar bar = sp.getHorizontalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void showFull(BufferedImage img) {
        JDialog dlg = new JDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Photo", true);
        dlg.setLayout(new BorderLayout());
        JLabel lbl = new JLabel(new ImageIcon(img));
        lbl.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        dlg.add(lbl, BorderLayout.CENTER);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }
}