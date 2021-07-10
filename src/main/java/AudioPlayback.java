import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class AudioPlayback {
    private FFmpegFrameGrabber fg;
    private AudioFormat af = null;
    private DataLine.Info dataLineInfo;
    private SourceDataLine sourceDataLine;
    private Buffer[] buf;
    private FloatBuffer leftData, rightData;
    private ShortBuffer ILData, IRData;
    private ByteBuffer TLData, TRData;
    private float vol = 1; //volume
    private int sampleFormat;
    private byte[] tl, tr;
    private byte[] combine;

    private long frameRead;
    private double frameTimeDeltaMilli;
    private double thresholdMilli;
    private AtomicLong frameToSkip = new AtomicLong(0);
    private AtomicLong delayTime = new AtomicLong(0);
    private long audioStartTimeNano = 0;

    public AudioPlayback(String filepath) throws FFmpegFrameGrabber.Exception {
        fg = new FFmpegFrameGrabber(filepath);
        init();
    }

    public AudioPlayback(File file) throws FFmpegFrameGrabber.Exception {
        fg = new FFmpegFrameGrabber(file);
        init();
    }

    private void init() throws FFmpegFrameGrabber.Exception {
        // Start frame grabber
        fg.start();
        frameRead = 0;
        // Calculate distance between frames
        long totalFrames = fg.getLengthInAudioFrames();
        double totalSeconds = (double) totalFrames / fg.getAudioFrameRate();
        frameTimeDeltaMilli = (totalSeconds * 1000) / totalFrames;

        sampleFormat = fg.getSampleFormat();
        // Sync threshold
        thresholdMilli = frameTimeDeltaMilli * 2;

        // Init sound output
        initSourceDataLine(fg);
    }

    public void start() {
        new Thread(() -> {
            Frame f;
            try {
                while (true) {
                    // Grab next audio frame
                    f = fg.grabSamples();
                    // Restart if reached last frame
                    if (f == null) {
                        fg.restart();
                        continue;
                    }
                    frameRead++;

                    // delay if delay value is bigger than 0
                    long delay = delayTime.getAndSet(0);
                    if (delay > 0) {
                        System.out.println("Delaying audio " + delay + " ms");
                        Thread.sleep(delay);
                    }

                    // skip frame if skip value is bigger than 0
                    if (frameToSkip.get() > 0) {
                        System.out.println("Skipped 1 audio frame");
                        frameToSkip.decrementAndGet();
                        continue;
                    }

                    // Initiate stream start time if not initiated
                    if (audioStartTimeNano == 0) audioStartTimeNano = System.nanoTime();
                    // Process audio and write to output device
                    processAudio(f.samples);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Function to sync video playback and audio playback
     * This function is called by video playback
     * */
    public void sync(long videoStartTimeNano, double videoElapsedTimeMilli) {
        // Do nothing if one of streams is not started
        if (videoStartTimeNano == 0 || audioStartTimeNano == 0) {
            System.out.println("Stream(s) not started");
            // video hasn't started yet
            return;
        }

        // Sync if starting points of two streams are not the same
        if (audioStartTimeNano < videoStartTimeNano) {
            // if audio start earlier than video, delay so that start time matches
            double delta = videoStartTimeNano - audioStartTimeNano;
            long frameDiff = (long) (delta / frameTimeDeltaMilli) / 1000000;
            delayTime.set((long) (frameDiff * frameTimeDeltaMilli));
            audioStartTimeNano = videoStartTimeNano;
            return;
        } else if (audioStartTimeNano > videoStartTimeNano) {
            // if video start earlier than audio, skip frame so that start time matches
            double delta = audioStartTimeNano - videoStartTimeNano;
            long frameDiff = (long) (delta / frameTimeDeltaMilli) / 1000000;
            frameToSkip.set(frameDiff);
            audioStartTimeNano = videoStartTimeNano;
            return;
        }

        // Calculate time difference between streams
        double audioElapsedTimeMilli = getElapsedTimeMilli();
        double deltaMilli = audioElapsedTimeMilli - videoElapsedTimeMilli;
        // Calculate number of audio frames difference from current video time position
        long frameDiff = (long) (Math.abs(deltaMilli) / frameTimeDeltaMilli);
        if (deltaMilli < -thresholdMilli) {
            // video is faster than audio
            // if difference is bigger than threshold, skip some frames
            frameToSkip.set(frameDiff);
        } else if (deltaMilli > thresholdMilli) {
            // audio is faster than video
            // if difference is bigger than threshold, delay the audio
            delayTime.set((long) (frameDiff * frameTimeDeltaMilli));
        }
    }

    private void processAudio(Buffer[] samples) {
        int k;
        buf = samples;
        switch (sampleFormat) {
            case avutil.AV_SAMPLE_FMT_FLTP://Float-type left and right channels are separated.
                leftData = (FloatBuffer) buf[0];
                TLData = floatToByteValue(leftData, vol);
                rightData = (FloatBuffer) buf[1];
                TRData = floatToByteValue(rightData, vol);
                tl = TLData.array();
                tr = TRData.array();
                combine = new byte[tl.length + tr.length];
                k = 0;
                for (int i = 0; i < tl.length; i = i + 2) { //Mix two channels.
                    for (int j = 0; j < 2; j++) {
                        combine[j + 4 * k] = tl[i + j];
                        combine[j + 2 + 4 * k] = tr[i + j];
                    }
                    k++;
                }
                writeToSourceDataLine(combine, 0, combine.length);
                break;
            case avutil.AV_SAMPLE_FMT_S16://Non-planar left and right channels are in one buffer.
                ILData = (ShortBuffer) buf[0];
                TLData = shortToByteValue(ILData, vol);
                tl = TLData.array();
                writeToSourceDataLine(tl, 0, tl.length);
                break;
            case avutil.AV_SAMPLE_FMT_FLT://float non-planar
                leftData = (FloatBuffer) buf[0];
                TLData = floatToByteValue(leftData, vol);
                tl = TLData.array();
                writeToSourceDataLine(tl, 0, tl.length);
                break;
            case avutil.AV_SAMPLE_FMT_S16P://float type left and right channels separated
                ILData = (ShortBuffer) buf[0];
                IRData = (ShortBuffer) buf[1];
                TLData = shortToByteValue(ILData, vol);
                TRData = shortToByteValue(IRData, vol);
                tl = TLData.array();
                tr = TRData.array();
                combine = new byte[tl.length + tr.length];
                k = 0;
                for (int i = 0; i < tl.length; i = i + 2) {
                    for (int j = 0; j < 2; j++) {
                        combine[j + 4 * k] = tl[i + j];
                        combine[j + 2 + 4 * k] = tr[i + j];
                    }
                    k++;
                }
                writeToSourceDataLine(combine, 0, combine.length);
                break;
            default:
                JOptionPane.showMessageDialog(null, "unsupport audio format", "unsupport audio format", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
                break;
        }
    }

    private void writeToSourceDataLine(byte[] data, int offset, int length){
        sourceDataLine.write(data, offset, length);
    }

    private double getElapsedTimeMilli() {
        return frameRead * frameTimeDeltaMilli;
    }

    private void initSourceDataLine(FFmpegFrameGrabber fg) {
        switch (fg.getSampleFormat()) {
            case avutil.AV_SAMPLE_FMT_U8://unsigned short 8bit
                break;
            case avutil.AV_SAMPLE_FMT_S16://signed short 16bit
            case avutil.AV_SAMPLE_FMT_FLT:
            case avutil.AV_SAMPLE_FMT_S16P://signed short 16bit, flat
            case avutil.AV_SAMPLE_FMT_FLTP://float flat type needs to be converted to 16bit short
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 16, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_S32:
                break;
            case avutil.AV_SAMPLE_FMT_DBL:
                break;
            case avutil.AV_SAMPLE_FMT_U8P:
                break;
            case avutil.AV_SAMPLE_FMT_S32P://signed short 32bit, flat type, but if it is 32bit, the computer sound card may not support it, this kind of music is also rare
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 32, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_DBLP:
                break;
            case avutil.AV_SAMPLE_FMT_S64://signed short 64bit non-flat
                break;
            case avutil.AV_SAMPLE_FMT_S64P://signed short 64bit flat type
                break;
            default:
                System.out.println("Unsupported music format");
                System.exit(0);
        }
        dataLineInfo = new DataLine.Info(SourceDataLine.class, af, AudioSystem.NOT_SPECIFIED);
        try {
            sourceDataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            sourceDataLine.open(af);
            sourceDataLine.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public static ByteBuffer shortToByteValue(ShortBuffer arr, float vol) {
        int len = arr.capacity();
        ByteBuffer bb = ByteBuffer.allocate(len * 2);
        for (int i = 0; i < len; i++) {
            bb.putShort(i * 2, (short) ((float) arr.get(i) * vol));
        }
        return bb; // switch to big endian by default
    }

    public static ByteBuffer floatToByteValue(FloatBuffer arr, float vol) {
        int len = arr.capacity();
        float f;
        float v;
        ByteBuffer res = ByteBuffer.allocate(len * 2);
        v = 32768.0f * vol;
        for (int i = 0; i < len; i++) {
            f = arr.get(i) * v;//Ref：https://stackoverflow.com/questions/15087668/how-to-convert-pcm-samples-in-byte-array-as-floating-point-numbers-in-the-range
            if (f > v) f = v;
            if (f < -v) f = v;
            //The default is converted to big endian
            res.putShort(i * 2, (short) f);//Pay attention to multiplying by 2, because two bytes are written at a time.
        }
        return res;
    }
}
