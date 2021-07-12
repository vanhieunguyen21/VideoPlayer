package server;

import javax.swing.*;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        String videoPath = "tmp/nuvole.mp4";
        String tempAudioPath = "tmp/input.wav";

        Runtime runtime = Runtime.getRuntime();
        runtime.exec("ffmpeg -y -i " + videoPath + " -ac 2 " + tempAudioPath);

        VideoPlayback videoPlayback = new VideoPlayback(videoPath);
        AudioPlayback audioPlayback = new AudioPlayback(tempAudioPath);

        JFrame frame = new JFrame("Video Player");
        frame.setContentPane(videoPlayback.getPanel());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        videoPlayback.start(audioPlayback);
    }
}
