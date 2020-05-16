package ch.bubendorf.hgt;

import org.jaitools.imageutils.ImageUtils;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import java.awt.image.Raster;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageUtil {
    private static final Logger LOG = Logger.getLogger(ImageUtil.class.getName());

    private final static boolean cubicInterpolation = true;

    /**
     * Converts HGT to tiled image
     */
    public static TiledImage buildImage(Integer[] words, int widthAndHeight) {
        LOG.log(Level.INFO, "Convert to tiled image ... BEGIN");
        TiledImage tiledImage = ImageUtils.createImageFromArray(words, widthAndHeight, widthAndHeight);
        LOG.log(Level.INFO, "Convert to tiled image ... END");

        return tiledImage;
    }

    public static PlanarImage resizeImage(PlanarImage image, int destSize) {
        Raster sourceRaster = image.getData();
        TiledImage destImage = new TiledImage(0, 0, destSize, destSize, 0, 0, image.getSampleModel(), image.getColorModel());
        int srcSize = image.getWidth();
        double scale = (destSize + 1.0) / srcSize;
        for (int y = 0; y < destImage.getHeight(); y++) {
            double srcYDouble = y / scale;
            int srcY = (int) Math.floor(srcYDouble);
            double fracY = srcYDouble - srcY;
            for (int x = 0; x < destImage.getWidth(); x++) {
                double srcXDouble = x / scale;
                int srcX = (int) Math.floor(srcXDouble);
                double fracX = srcXDouble - srcX;

                int value;
                int v11 = getSample(sourceRaster, srcX, srcY);
                int v21 = getSample(sourceRaster, srcX + 1, srcY);
                int v12 = getSample(sourceRaster, srcX, srcY + 1);
                int v22 = getSample(sourceRaster, srcX + 1, srcY + 1);

                if (cubicInterpolation) {
                    int v00 = getSample(sourceRaster, srcX - 1, srcY - 1);
                    int v01 = getSample(sourceRaster, srcX - 1, srcY);
                    int v02 = getSample(sourceRaster, srcX - 1, srcY + 1);
                    int v03 = getSample(sourceRaster, srcX - 1, srcY + 2);

                    int v10 = getSample(sourceRaster, srcX, srcY - 1);
                    int v13 = getSample(sourceRaster, srcX, srcY + 2);

                    int v20 = getSample(sourceRaster, srcX + 1, srcY - 1);
                    int v23 = getSample(sourceRaster, srcX + 1, srcY + 2);

                    int v30 = getSample(sourceRaster, srcX + 2, srcY - 1);
                    int v31 = getSample(sourceRaster, srcX + 2, srcY);
                    int v32 = getSample(sourceRaster, srcX + 2, srcY + 1);
                    int v33 = getSample(sourceRaster, srcX + 2, srcY + 2);
                    // Wenn einer der Werte einen NoData Wert enthält, dann wird
                    // die Bilineare Interpolation der inneren vier Werte gemacht.
                    // Diese können natürlich ebenfalls NoData Werte enthalten.
                    // Das Resultat ist dann NoData!
                    if (isNoData(v00, v01, v02, v03, v10, v11, v12, v13, v20, v21, v22, v23, v30, v31, v32, v33)) {
                        value = bilinear(fracX, fracY, v11, v21, v12, v22);
                    } else {
                        double v0 = cubic(fracY, v00, v01, v02, v03);
                        double v1 = cubic(fracY, v10, v11, v12, v13);
                        double v2 = cubic(fracY, v20, v21, v22, v23);
                        double v3 = cubic(fracY, v30, v31, v32, v33);
                        value = (int) Math.round(cubic(fracX, v0, v1, v2, v3));
                    }
                } else {
                    value = bilinear(fracX, fracY, v11, v21, v12, v22);
                }
                destImage.setSample(x, y, 0, value);
            }
        }
        return destImage;
    }

    private static int getSample(Raster raster, int x, int y) {
        x = Math.max(0, Math.min(x, raster.getHeight() - 1));
        y = Math.max(0, Math.min(y, raster.getWidth() - 1));
        return raster.getSample(x, y, 0);
    }

    private static double linear(double f, int v1, int v2) {
        return v1 + (v2 - v1) * f;
    }

    private static double linear(double f, double v1, double v2) {
        return v1 + (v2 - v1) * f;
    }

    private static int bilinear(double xFrac, double yFrac, int v00, int v01, int v10, int v11) {
        if (isNoData(v00) || isNoData(v01) || isNoData(v10) || isNoData(v11)) {
            return Integer.MIN_VALUE;
        }
        double l1 = linear(xFrac, v00, v01);
        double l2 = linear(xFrac, v10, v11);
        double r = linear(yFrac, l1, l2);
        return (int) Math.round(r);
    }

    private static boolean isNoData(int v) {
        return Integer.MIN_VALUE == v;
    }

    private static boolean isNoData(int... values) {
        for (int v : values) {
            if (isNoData(v)) {
                return true;
            }
        }
        return false;
    }

    private static double cubic(double f, int p0, int p1, int p2, int p3) {
        return p1 + 0.5 * f * (p2 - p0 + f * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 + f * (3.0 * (p1 - p2) + p3 - p0)));
    }

    private static double cubic(double f, double p0, double p1, double p2, double p3) {
        return p1 + 0.5 * f * (p2 - p0 + f * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3 + f * (3.0 * (p1 - p2) + p3 - p0)));
    }
}
