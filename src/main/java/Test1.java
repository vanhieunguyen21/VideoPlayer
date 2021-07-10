import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.*;
import javax.swing.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class Test1 {
    static AudioFormat af = null;
    static DataLine.Info dataLineInfo;
    static SourceDataLine sourceDataLine;

    public static void main(String[] args) throws FFmpegFrameGrabber.Exception, InterruptedException {
        FFmpegFrameGrabber fg = new FFmpegFrameGrabber("tmp/audio.wav");
        fg.start();
        sampleFormat = fg.getSampleFormat();
        initSourceDataLine(fg);

        long totalFrames = fg.getLengthInAudioFrames();
        double totalSeconds = (double) totalFrames / fg.getAudioFrameRate();

        Frame f;
        int count=0;
        System.out.println(totalSeconds/(totalFrames));
        while (true) {
            f = fg.grabSamples();
            if (f == null) {
                fg.stop();
                break;
            }
//            count++;
//            System.out.println("Elapsed "+ ((double) count / (double) totalFrames) * totalSeconds);
            processAudio(f.samples);
        }
        System.out.println(count);
    }

    static Buffer[] buf;
    static FloatBuffer leftData, rightData;
    static ShortBuffer ILData, IRData;
    static ByteBuffer TLData, TRData;
    static float vol = 1;//volume
    static int sampleFormat;
    static byte[] tl, tr;
    static byte[] combine;

    public static void processAudio(Buffer[] samples) {
        int k;
        buf = samples;
        switch (sampleFormat) {
            case avutil.AV_SAMPLE_FMT_FLTP://Flat-type left and right channels are separated.
                leftData = (FloatBuffer) buf[0];
                TLData = floatToByteValue(leftData, vol);
                rightData = (FloatBuffer) buf[1];
                TRData = floatToByteValue(leftData, vol);
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
                sourceDataLine.write(combine, 0, combine.length);
                break;
            case avutil.AV_SAMPLE_FMT_S16://Non-planar left and right channels are in one buffer.
                ILData = (ShortBuffer) buf[0];
                TLData = shortToByteValue(ILData, vol);
                tl = TLData.array();
                sourceDataLine.write(tl, 0, tl.length);
                break;
            case avutil.AV_SAMPLE_FMT_FLT://float non-planar
                leftData = (FloatBuffer) buf[0];
                TLData = floatToByteValue(leftData, vol);
                tl = TLData.array();
                sourceDataLine.write(tl, 0, tl.length);
                break;
            case avutil.AV_SAMPLE_FMT_S16P://flat type left and right channels separated
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
                sourceDataLine.write(combine, 0, combine.length);
                break;
            default:
                JOptionPane.showMessageDialog(null, "unsupport audio format", "unsupport audio format", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
                break;
        }
    }


    public static void initSourceDataLine(FFmpegFrameGrabber fg) {
        switch (fg.getSampleFormat()) {
            case avutil.AV_SAMPLE_FMT_U8://unsigned short 8bit
                break;
            case avutil.AV_SAMPLE_FMT_S16://signed short 16bit
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 16, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_S32:
                break;
            case avutil.AV_SAMPLE_FMT_FLT:
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 16, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_DBL:
                break;
            case avutil.AV_SAMPLE_FMT_U8P:
                break;
            case avutil.AV_SAMPLE_FMT_S16P://signed short 16bit, flat
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 16, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_S32P://signed short 32bit, flat type, but if it is 32bit, the computer sound card may not support it, this kind of music is also rare
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 32, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
                break;
            case avutil.AV_SAMPLE_FMT_FLTP://float flat type needs to be converted to 16bit short
                af = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fg.getSampleRate(), 16, fg.getAudioChannels(), fg.getAudioChannels() * 2, fg.getSampleRate(), true);
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
        dataLineInfo = new DataLine.Info(SourceDataLine.class,
                af, AudioSystem.NOT_SPECIFIED);
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
