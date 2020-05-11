package ch.bubendorf.hgt;

import org.jaitools.imageutils.ImageUtils;

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.renderable.ParameterBlock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;

public class ImageUtil {
    private static final Logger LOG = Logger.getLogger(ImageUtil.class.getName());

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
        // See https://github.com/NetLogo/GIS-Extension/issues/4
        System.setProperty("com.sun.media.jai.disableMediaLib", "true");

        float scale = (float)destSize / image.getWidth();
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
//        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
        RenderingHints renderHints = new RenderingHints(KEY_RENDERING, VALUE_RENDER_QUALITY);
        BorderExtender extender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
        renderHints.add(new RenderingHints(JAI.KEY_BORDER_EXTENDER , extender));
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image);
        pb.add(scale);
        pb.add(scale);
        pb.add(0.0F);
        pb.add(0.0F);
        pb.add(interpolation);
        final PlanarImage resizedImage = JAI.create("scale", pb, renderHints).createInstance();
        LOG.log(Level.FINER, "Image resized to : " + resizedImage.getWidth() + "x"
                + resizedImage.getHeight());
        return resizedImage;
    }
}
