package ch.bubendorf.hgt;

import org.jaitools.media.jai.contour.ContourDescriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;

import javax.media.jai.PlanarImage;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ContourOpImageTest {

    public static final int NODATA = -1;

    @Test
    public void simple() {
        Integer[] words = {10, 20, 30, 40, 50,
        20, 30, 40, 50, 60,
        30, 40, 50, 60, 70,
        40, 50, 60, 70, 80,
        50, 60, 70, 80, 90};

        Collection<LineString> lines = getLineStrings(words, false,35, 60);

        assertNotNull(lines);
        assertEquals(2, lines.size());
    }

    @Test
    public void flat() {
        Integer[] words = {
                100,100,100,100,100,
                100,150,150,150,100,
                100,150,150,150,100,
                100,150,150,150,100,
                100,100,100,100,100};

        Collection<LineString> lines150 = getLineStrings(words, false, 150);
        assertNotNull(lines150);
        assertEquals(1, lines150.size());

        Collection<LineString> lines160 = getLineStrings(words, false,160);
        assertNotNull(lines160);
        assertEquals(0, lines160.size());
    }

    @Test
    public void nodata() {
        Integer[] words = {
                100,100,100,100,100,
                100,150,150,150,100,
                100,150, NODATA,150,100,
                100,150,150,150,100,
                100,100,100,100,100};

        Collection<LineString> lines150 = getLineStrings(words, true,150);
        assertNotNull(lines150);
        assertEquals(1, lines150.size());

        Collection<LineString> lines160 = getLineStrings(words, false,160);
        assertNotNull(lines160);
        assertEquals(0, lines160.size());
    }

    @Test
    public void three() {
        Integer[] words = {
                NODATA,100,150,
                100,150,200,
                150,200,250};

        Collection<LineString> lines125 = getLineStrings(words, false,125);
        assertEquals(1, lines125.size());
    }

    @Test
    public void hofuhre() {
        Integer[] words = {
                418,418,418,418,417,418,
                420,419,419,420,420,420,
                420,420,420,420,NODATA,NODATA,
                421,421,421,420,NODATA,NODATA,
                422,421,421,421,420,NODATA,
                420,420,420,420,420,420
                };

        Collection<LineString> lines420 = getLineStrings(words, false,420);
        assertEquals(1, lines420.size());
    }

    private Collection<LineString> getLineStrings(Integer[] words, boolean strictNodata, Integer... levelInts) {
        PlanarImage tiledImage = ImageUtil.buildImage(words, (int)Math.sqrt(words.length));
        final Collection<Integer> levels = Arrays.asList(levelInts);

        final Collection<Object> noDatas = Arrays.asList(NODATA);
        ContourOpImage contourOpImage = new ContourOpImage(tiledImage, null, 0, levels,
                0.0, noDatas, strictNodata, true, false);
        return (Collection<LineString>) contourOpImage.getProperty(ContourDescriptor.CONTOUR_PROPERTY_NAME);
    }
}
