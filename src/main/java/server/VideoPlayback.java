package server;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class VideoPlayback {
    private final FFmpegFrameGrabber fg;
    private JPanel panel;
    private JLabel image;
    private final double frameTimeDelta;
    private long videoStartTimeNano = 0;
    private long frameRead;

    public VideoPlayback(String filepath) throws IOException {
        fg = new FFmpegFrameGrabber(filepath);
        fg.start();
        frameTimeDelta = 1000 / fg.getVideoFrameRate();
        image.setPreferredSize(new Dimension(fg.getImageWidth(), fg.getImageHeight()));
    }

    public void start(AudioPlayback audioPlayback) {
        new Thread(() -> {
            try {
                audioPlayback.start();
                videoStartTimeNano = 0;
                frameRead = 0;
                double nextFrameTime = 0;
                Frame f;

                while (true) {
                    f = fg.grabImage();
                    if (f == null) {
                        fg.restart();
                        continue;
                    }
                    // Init video started time
                    if (videoStartTimeNano == 0) videoStartTimeNano = System.nanoTime();

                    // Wait for a remaining time till next frame
                    long remaining = (long) (nextFrameTime - System.nanoTime());
                    if (remaining > 0) {
                        Thread.sleep(remaining / 1000000);
                    }

                    frameRead++;
                    nextFrameTime = videoStartTimeNano + frameRead * frameTimeDelta * 1000000;
                    image.setIcon(new ImageIcon(new Java2DFrameConverter().getBufferedImage(f)));
                    audioPlayback.sync(videoStartTimeNano, getElapsedTimeMilli());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public double getElapsedTimeMilli() {
        return frameRead * frameTimeDelta;
    }

    public JPanel getPanel() {
        return panel;
    }
}
