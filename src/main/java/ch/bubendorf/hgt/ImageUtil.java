package ch.bubendorf.hgt;

/*import it.geosolutions.jaiext.interpolators.InterpolationBicubic;
import it.geosolutions.jaiext.interpolators.InterpolationBilinear;
import it.geosolutions.jaiext.range.Range;
import it.geosolutions.jaiext.range.RangeFactory;
import it.geosolutions.jaiext.rescale.RescaleCRIF;
import it.geosolutions.jaiext.rescale.RescaleDescriptor;
import it.geosolutions.jaiext.scale.Scale2CRIF;
import it.geosolutions.jaiext.scale.Scale2Descriptor;
import it.geosolutions.jaiext.utilities.*;*/

import org.jaitools.imageutils.ImageUtils;

import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import java.awt.image.Raster;
import java.util.logging.Level;
import java.util.logging.Logger;

//import it.geosolutions.jaiext.interpolators.*;

public class ImageUtil {
    private static final Logger LOG = Logger.getLogger(ImageUtil.class.getName());

    private static boolean cubicInterpolation = true;

    /*static {
        try {
            OperationRegistry registry = JAI.getDefaultInstance().getOperationRegistry();

            registry.registerDescriptor(new Scale2Descriptor());
            RIFRegistry.register(registry, "Scale2", "it.geosolutions.jaiext.scale", new Scale2CRIF());

            registry.registerDescriptor(new RescaleDescriptor());
            RIFRegistry.register(registry, "Rescale", "it.geosolutions.jaiext.rescale", new RescaleCRIF());
       } catch (Exception ignored) {
        }
    } */

    /**
     * Converts HGT to tiled image
     */
    public static TiledImage buildImage(Integer[] words, int widthAndHeight) {
        LOG.log(Level.INFO, "Convert to tiled image ... BEGIN");
        TiledImage tiledImage = ImageUtils.createImageFromArray(words, widthAndHeight, widthAndHeight);
        LOG.log(Level.INFO, "Convert to tiled image ... END");

        return tiledImage;
    }

    /*public static PlanarImage resizeImageAlt(PlanarImage image, int destSize) {
        // See https://github.com/NetLogo/GIS-Extension/issues/4
        System.setProperty("com.sun.media.jai.disableMediaLib", "true");

        float scale = (float) destSize / image.getWidth();
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
//        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
        RenderingHints renderHints = new RenderingHints(KEY_RENDERING, VALUE_RENDER_QUALITY);
        BorderExtender extender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
        renderHints.add(new RenderingHints(JAI.KEY_BORDER_EXTENDER, extender));
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(scale);
        pb.add(scale);
        pb.add(0.0F);
        pb.add(0.0F);
        pb.add(interpolation);
        final PlanarImage resizedImage = JAI.create("scale", pb, renderHints).createInstance();
        return resizedImage;
    }*/

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

                int value = 0;
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
        // Wenn NoData Werte vorkommen wird einfach der Durchschnitt der anderen Werte berechnet.
        if (isNoData(v00) || isNoData(v01) || isNoData(v10) || isNoData(v11)) {
            return Integer.MIN_VALUE;
/*            int sum = 0;
            int count = 0;
            if (!isNoData(v00)) {
                sum += v00;
                count++;
            }
            if (!isNoData(v01)) {
                sum += v01;
                count++;
            }
            if (!isNoData(v10)) {
                sum += v10;
                count++;
            }
            if (!isNoData(v11)) {
                sum += v11;
                count++;
            }
            if (count == 0) {
                return Integer.MIN_VALUE;
            }
            return (int)Math.round((double)sum / count);*/
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

    /*public static PlanarImage resizeImageNeu(PlanarImage image, int destSize) {
        // See https://github.com/NetLogo/GIS-Extension/issues/4
        System.setProperty("com.sun.media.jai.disableMediaLib", "true");

        double[] scales = new double[]{2.0d};
        double[] offsets= new double[]{0.0d};
        boolean useRoiAccessor = false;

        double scale = (double)destSize / image.getWidth();
        double destNoData = -1.0d;
//        ROI roi= new ROIShape(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        ROI roi = null;
        Range noDataRange = null;

//        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
//        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
//        Interpolation interpolation = new InterpolationBicubic(8, noDataRange, false,
//                -1.0, DataBuffer.TYPE_INT, false,8);
        Interpolation interpolation = new InterpolationBilinear(8, noDataRange, useRoiAccessor,
                destNoData, DataBuffer.TYPE_INT);

        RenderingHints renderHints = new RenderingHints(KEY_RENDERING, VALUE_RENDER_QUALITY);
        BorderExtender extender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
//        BorderExtender extender = BorderExtender.createInstance(BorderExtender.BORDER_REFLECT);
//        BorderExtender extender = BorderExtender.createInstance(BorderExtender.BORDER_ZERO);
        renderHints.add(new RenderingHints(JAI.KEY_BORDER_EXTENDER , extender));
        double[] backgroundValues = new double[]{-1};

        RenderedOp op = Scale2Descriptor.create(image, scale, scale, 0.0, 0.0,
                interpolation, roi, useRoiAccessor, noDataRange, backgroundValues, renderHints);

//        Raster tile = op.getTile(0, 0);
//        Raster[] rasters = op.getTiles();
        return op.getRendering();
    }*/

    /*public static PlanarImage resizeImage(PlanarImage image, int destSize) {
        // See https://github.com/NetLogo/GIS-Extension/issues/4
        System.setProperty("com.sun.media.jai.disableMediaLib", "true");

        int minx = image.getMinX(); // Minimum X value of the image
        int miny = image.getMinY(); // Minimum Y value of the image
        int width = image.getWidth(); // Image Width
        int height= image.getHeight(); // Image Height

        double[] scales = new double[]{2.0d};
        double[] offsets= new double[]{1.0d};

        ROI roi= new ROIShape(new Rectangle(minx, miny, width, (int)(height/2)));
//        ROI roi = null;

        byte value= 0;
        boolean minIncluded = true;
        boolean maxIncluded = true;

        Range noDataRange = RangeFactory.create(value, minIncluded, value, maxIncluded);

        boolean useRoiAccessor = false;

        double destNoData = 0.0d;
        RenderingHints hints = null;
        RenderedOp rescaled = RescaleDescriptor.create(image, scales, offsets, roi,
                noDataRange, useRoiAccessor, destNoData, hints);

        Raster[] data = rescaled.getTiles();

        return rescaled.getRendering();
    }*/
}
