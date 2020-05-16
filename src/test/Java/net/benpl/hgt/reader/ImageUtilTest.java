package net.benpl.hgt.reader;

import ch.bubendorf.hgt.ImageUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.media.jai.PlanarImage;
import java.awt.image.Raster;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class ImageUtilTest {

    @Tag("ignore")
    @Test
    public void scale() {
        Integer[] words = new Integer[]{
                100, 200, 300,
                200, 300, 400,
                300, 400, 500};

        PlanarImage tiledImage = ImageUtil.buildImage(words, 3);
        System.out.println("Vorher");
        dumpImage(tiledImage);
        PlanarImage scaledImage = ImageUtil.resizeImage(tiledImage, 5);
        System.out.println("Nachher");
        dumpImage(scaledImage);
    }

    @Tag("ignore")
    @Test
    public void flatSurface() {
        Integer[] words = new Integer[]{
                100, 100, 100, 100, 100,
                100, 200, 200, 200, 100,
                100, 200, 200, 200, 100,
                100, 200, 200, 200, 100,
                100, 100, 100, 100, 100,};

        PlanarImage tiledImage = ImageUtil.buildImage(words, 5);
        System.out.println("Vorher");
        dumpImage(tiledImage);
        PlanarImage scaledImage = ImageUtil.resizeImage(tiledImage, 9);
        System.out.println("Nachher");
        dumpImage(scaledImage);
    }

    //    @Tag("ignore")
    @Test
    public void nodata() {
        Integer[] words = new Integer[]{
                100, 100, 100, 100, 100,
                100, 200, 200, 200, 100,
                100, 200, 200, 200, 100,
                100, 200, 200, Integer.MIN_VALUE, 100,
                100, 100, 100, 100, 100,};

        PlanarImage tiledImage = ImageUtil.buildImage(words, 5);
        System.out.println("Vorher");
        dumpImage(tiledImage);
        PlanarImage scaledImage = ImageUtil.resizeImage(tiledImage, 9);
        System.out.println("Nachher");
        dumpImage(scaledImage);
    }

    @Tag("ignore")
    @Test
    public void scaleHGT() throws IOException {
        Integer[] words = loadFile("C:\\Geo\\GCRouter\\data\\dem\\04-NASA\\N47E007.hgt", 1201);

        PlanarImage tiledImage = ImageUtil.buildImage(words, 1201);
        System.out.println("Vorher");
        dumpImage(tiledImage);
        PlanarImage scaledImage = ImageUtil.resizeImage(tiledImage, 2401);
        System.out.println("Nachher");
        dumpImage(scaledImage);
    }

    private void dumpImage(PlanarImage image) {
        final Raster imageData = image.getData();
        for (int y = 0; y < image.getWidth() && y < 10; y++) {
            System.out.print(y + ":");
            for (int x = 0; x < image.getHeight() && x < 20; x++) {

                final int sample = imageData.getSample(x, y, 0);
                if (sample == Integer.MIN_VALUE) {
                    System.out.print("ND, ");
                } else {
                    System.out.print(sample + ", ");
                }
            }
            System.out.println();
        }
    }

    private Integer[] loadFile(String file, int widthAndHeight) throws IOException {
        byte[] bytes = new byte[widthAndHeight * widthAndHeight * 2];
        Integer[] words = new Integer[widthAndHeight * widthAndHeight];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            dis.readFully(bytes);

            for (int i = 0; i < words.length; i++) {
                words[i] = ((bytes[2 * i] << 8) & 0xff00) | (bytes[2 * i + 1] & 0x00ff);
            }
        }
        return words;
    }

}
