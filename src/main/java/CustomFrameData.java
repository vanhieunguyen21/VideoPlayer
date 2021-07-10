import java.awt.image.BufferedImage;

class CustomFrameData {
    BufferedImage bi;
    double frameTime;

    public CustomFrameData(BufferedImage bi, double frameTime) {
        this.bi = bi;
        this.frameTime = frameTime;
    }
}