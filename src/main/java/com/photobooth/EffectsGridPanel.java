package com.photobooth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class EffectsGridPanel extends JPanel {

    private static final int CELL_W = 240;
    private static final int CELL_H = 180;
    private static final int GAP    = 4;

    // Paginated list of effects — 9 per page
    public static final FilterEngine.Effect[] PAGE_1 = {
        FilterEngine.Effect.SEPIA,
        FilterEngine.Effect.BLACK_WHITE,
        FilterEngine.Effect.XRAY,
        FilterEngine.Effect.THERMAL,
        FilterEngine.Effect.POP_ART,
        FilterEngine.Effect.GLOW,
        FilterEngine.Effect.ROSE_TINT,
        FilterEngine.Effect.PLASTIC_CAMERA,
        FilterEngine.Effect.NORMAL
    };
    public static final FilterEngine.Effect[] PAGE_2 = {
        FilterEngine.Effect.BULGE,
        FilterEngine.Effect.SQUEEZE,
        FilterEngine.Effect.TWIRL,
        FilterEngine.Effect.FISHEYE,
        FilterEngine.Effect.STRETCH,
        FilterEngine.Effect.MIRROR,
        FilterEngine.Effect.LIGHT_TUNNEL,
        FilterEngine.Effect.HALFTONE,
        FilterEngine.Effect.INVERT
    };
    public static final FilterEngine.Effect[][] PAGES = { PAGE_1, PAGE_2 };

    private final FrameSource source;
    private CaptureWorker worker;

    private BufferedImage rawFrame;
    private int selectedIndex = 0;
    private int currentPage = 0;

    private volatile boolean flashing = false;
    private volatile int countdown = -1;

    // Polaroid animation
    private volatile BufferedImage polaroidImage;
    private volatile double polaroidProgress = 0.0; // 0 → 1

    private final JLabel[] cells = new JLabel[9];
    private final JLabel[] nameLabels = new JLabel[9];

    private java.util.function.Consumer<BufferedImage> onCaptureFinished;

    public EffectsGridPanel(FrameSource source) {
        this.source = source;

        setLayout(new GridLayout(3, 3, GAP, GAP));
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        setPreferredSize(new Dimension(CELL_W * 3 + GAP * 4, CELL_H * 3 + GAP * 4));

        for (int i = 0; i < 9; i++) {
            final int idx = i;

            JLabel cell = new JLabel();
            cell.setOpaque(true);
            cell.setBackground(Color.DARK_GRAY);
            cell.setHorizontalAlignment(SwingConstants.CENTER);
            cell.setVerticalAlignment(SwingConstants.CENTER);
            cell.setPreferredSize(new Dimension(CELL_W, CELL_H));
            cell.setLayout(new BorderLayout());
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel name = new JLabel("", SwingConstants.CENTER);
            name.setOpaque(true);
            name.setBackground(new Color(0, 0, 0, 150));
            name.setForeground(Color.WHITE);
            name.setFont(new Font("Helvetica", Font.BOLD, 11));
            name.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            cell.add(name, BorderLayout.SOUTH);

            cell.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectedIndex = idx;
                    highlightSelection();
                }
            });

            cells[i] = cell;
            nameLabels[i] = name;
            add(cell);
        }
        updatePageLabels();
        highlightSelection();
    }

    public void setOnCaptureFinished(java.util.function.Consumer<BufferedImage> cb) {
        this.onCaptureFinished = cb;
    }

    public void startStreaming() {
        source.start();
        worker = new CaptureWorker(source, this);
        worker.execute();
    }

    public void stopStreaming() {
        if (worker != null) worker.stop();
        source.stop();
    }

    public void updateFrame(BufferedImage frame) {
        this.rawFrame = mirror(frame);
        BufferedImage small = scale(rawFrame, CELL_W, CELL_H);
        FilterEngine.Effect[] page = PAGES[currentPage];
        for (int i = 0; i < 9; i++) {
            BufferedImage preview = FilterEngine.apply(small, page[i]);
            cells[i].setIcon(new ImageIcon(preview));
        }
    }

    public BufferedImage captureSelected() {
        if (rawFrame == null) return null;
        return FilterEngine.apply(rawFrame, PAGES[currentPage][selectedIndex]);
    }

    public void setPage(int page) {
        if (page < 0 || page >= PAGES.length || page == currentPage) return;
        currentPage = page;
        selectedIndex = 0;
        updatePageLabels();
        highlightSelection();
        if (rawFrame != null) updateFrame(rawFrame);
    }

    public int getCurrentPage() { return currentPage; }
    public int getPageCount()   { return PAGES.length; }

    private void updatePageLabels() {
        FilterEngine.Effect[] page = PAGES[currentPage];
        for (int i = 0; i < 9; i++) {
            nameLabels[i].setText(page[i].label);
        }
    }

    public void showCountdown(int n) { this.countdown = n; repaint(); }
    public void clearCountdown()     { this.countdown = -1; repaint(); }
    public void showFlash()          { this.flashing = true; repaint(); }
    public void hideFlash()          { this.flashing = false; repaint(); }

    /**
     * Kicks off the Polaroid slide-down animation for the given image.
     * Duration ~500 ms. onCaptureFinished is called when the animation completes.
     */
    public void startPolaroidAnimation(BufferedImage img) {
        this.polaroidImage = img;
        this.polaroidProgress = 0.0;
        final long DURATION_MS = 500;
        final long start = System.currentTimeMillis();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            long elapsed = System.currentTimeMillis() - start;
            polaroidProgress = Math.min(1.0, elapsed / (double) DURATION_MS);
            repaint();
            if (polaroidProgress >= 1.0) {
                ((Timer) e.getSource()).stop();
                BufferedImage finished = polaroidImage;
                polaroidImage = null;
                if (onCaptureFinished != null) onCaptureFinished.accept(finished);
            }
        });
        t.start();
    }

    private void highlightSelection() {
        for (int i = 0; i < 9; i++) {
            boolean sel = (i == selectedIndex);
            cells[i].setBorder(BorderFactory.createLineBorder(
                sel ? Color.WHITE : Color.DARK_GRAY, sel ? 3 : 2));
            nameLabels[i].setBackground(
                sel ? new Color(200, 30, 30, 200) : new Color(0, 0, 0, 150));
        }
    }

    private BufferedImage mirror(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, w, 0, -w, h, null);
        g.dispose();
        return dst;
    }

    private BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        // Countdown
        if (countdown > 0) {
            g2.setFont(new Font("Helvetica", Font.BOLD, 200));
            g2.setColor(new Color(255, 255, 255, 200));
            String s = String.valueOf(countdown);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2,
                getHeight() / 2 + fm.getAscent() / 3);
        }

        // White flash
        if (flashing) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        // Polaroid slide-down
        if (polaroidImage != null) {
            int pw = (int)(getWidth() * 0.20);
            int ph = (int)(pw * 1.20); // aspect for the paper
            int cx = getWidth() / 2;
            int startY = getHeight() / 2 - ph / 2;
            int endY   = getHeight() + ph;   // slide below panel
            int y = (int)(startY + (endY - startY) * polaroidProgress);

            // Paper shadow
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(cx - pw/2 + 3, y + 3, pw, ph);

            // White paper
            g2.setColor(Color.WHITE);
            g2.fillRect(cx - pw/2, y, pw, ph);

            // Photo on paper
            int imgPad = 8;
            int imgW = pw - imgPad * 2;
            int imgH = (int)(imgW * (double) polaroidImage.getHeight() / polaroidImage.getWidth());
            g2.drawImage(polaroidImage, cx - pw/2 + imgPad, y + imgPad, imgW, imgH, null);
        }

        g2.dispose();
    }
}