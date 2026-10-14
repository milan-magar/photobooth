package com.photobooth;

import com.github.sarxos.webcam.Webcam;

import javax.swing.*;
import java.awt.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

public class PhotoBoothMain {

    public static void main(String[] args) {
        System.setProperty("apple.awt.brushMetalLook", "true");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new PhotoBoothMain().run());
    }

    private void run() {
        FrameSource source = chooseFrameSource();
        if (source == null) {
            System.exit(0);
        }

        JFrame frame = new JFrame("Photo Booth — " + source.getDescription());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(200, 200, 200));

        EffectsGridPanel effectsGrid = new EffectsGridPanel(source);
        FilmStripPanel filmStrip = new FilmStripPanel();
        ShutterBar shutterBar = new ShutterBar(effectsGrid, filmStrip);

        root.add(effectsGrid, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(200, 200, 200));
        south.add(shutterBar, BorderLayout.NORTH);
        south.add(filmStrip, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        effectsGrid.startStreaming();

        if (source instanceof HttpFrameSource http) {
            Timer statusTimer = new Timer(500, e -> {
                long ms = http.getMillisSinceLastFrame();
                String tag = (ms == Long.MAX_VALUE)
                    ? "waiting for frames..."
                    : (ms + " ms ago  |  received=" + http.getReceivedCount());
                frame.setTitle("Photo Booth — Phone (port "
                    + HttpFrameSource.PORT + ") — " + tag);
            });
            statusTimer.start();
        }

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                effectsGrid.stopStreaming();
            }
        });
    }

    private FrameSource chooseFrameSource() {
        String[] options = { "Use Webcam", "Use Phone (port 8080)", "Cancel" };

        int choice = JOptionPane.showOptionDialog(
            null,
            "Choose a video source for Photo Booth:",
            "Photo Booth — Video Source",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]);

        if (choice == 0) {
            return createWebcamSource();
        } else if (choice == 1) {
            return createPhoneSource();
        } else {
            return null;
        }
    }

    private FrameSource createWebcamSource() {
        Webcam webcam = null;
        try {
            webcam = Webcam.getDefault();
        } catch (Exception ignored) {}

        if (webcam == null) {
            int answer = JOptionPane.showConfirmDialog(
                null,
                "No webcam detected.\n\n" +
                "Would you like to use your phone as the camera instead?\n" +
                "(The app will listen on port 8080 for JPEG frames.)",
                "No Webcam",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (answer == JOptionPane.YES_OPTION) {
                return createPhoneSource();
            }
            return null;
        }

        System.out.println("[PhotoBooth] Using webcam: " + webcam.getName());
        return new WebcamFrameSource(webcam);
    }

    private FrameSource createPhoneSource() {
        HttpFrameSource src = new HttpFrameSource();
        src.start();
        showPhoneInstructions();
        return src;
    }

    private void showPhoneInstructions() {
        String ip = findLanIp();
        String msg =
            "Listening for frames on port 8080.\n\n" +
            "On your phone, POST each JPEG frame to:\n\n" +
            "    http://" + ip + ":8080/frame\n\n" +
            "Test from a browser on the same Wi-Fi:\n\n" +
            "    http://" + ip + ":8080/\n\n" +
            "Watch the terminal for [PhotoBooth] log lines.\n" +
            "The window title will show frame arrival stats.";

        JOptionPane.showMessageDialog(
            null, msg, "Phone Camera Mode",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private String findLanIp() {
        try {
            Enumeration<NetworkInterface> ifaces =
                NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(ifaces)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "localhost";
    }
}