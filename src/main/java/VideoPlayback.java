import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class VideoPlayback extends JPanel {
    private FFmpegFrameGrabber fg;
    private JPanel panel;
    private JLabel image;
    private double nextFrameTime;
    private long frameTimeDelta;
    private long streamStartTime = 0;
    private long frameRead;

    public VideoPlayback(String filepath) throws IOException {
        fg = new FFmpegFrameGrabber(filepath);
        fg.start();
        frameTimeDelta = (long) (1000 / fg.getVideoFrameRate());
        image.setPreferredSize(new Dimension(fg.getImageWidth(), fg.getImageHeight()));
    }

    public void start() {
        new Thread(() -> {
            try {
                streamStartTime = 0;
                frameRead = 0;
                nextFrameTime = 0;
                Frame f;

                while (true) {
                    f = fg.grabImage();
                    if (f == null) {
                        fg.restart();
                        continue;
                    }
                    if (streamStartTime == 0) streamStartTime = System.nanoTime();
                    while (System.nanoTime() < nextFrameTime) {
                        // wait till next frame time
                    }
                    frameRead++;
                    nextFrameTime = streamStartTime + frameRead * frameTimeDelta * 1000000;
                    image.setIcon(new ImageIcon(new Java2DFrameConverter().getBufferedImage(f)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public long getElapsedTime() {
        return frameRead * frameTimeDelta;
    }

    public long getStreamStartTime() {
        return streamStartTime;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String videoPath = "tmp/input.mp4";
        String tempAudioPath = "tmp/audio_tmp.wav";

        Runtime runtime = Runtime.getRuntime();
        runtime.exec("ffmpeg -y -i "+videoPath+" -ac 1 -filter:a \"volume=5\" "+tempAudioPath);

        VideoPlayback videoPlayback = new VideoPlayback(videoPath);
        AudioPlayback audioPlayback = new AudioPlayback(tempAudioPath);

        JFrame frame = new JFrame("VideoPlayer");
        frame.setContentPane(videoPlayback.panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        audioPlayback.start();
        videoPlayback.start();

        while (true) {
            audioPlayback.sync(videoPlayback.getStreamStartTime(), videoPlayback.getElapsedTime());
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
