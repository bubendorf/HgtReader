package net.benpl.hgt.reader;

import org.jaitools.imageutils.ImageUtils;
import org.jaitools.media.jai.contour.ContourDescriptor;
import org.jaitools.media.jai.contour.ContourRIF;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.openstreetmap.osmosis.core.container.v0_6.BoundContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.sort.v0_6.EntityByTypeThenIdComparator;
import org.openstreetmap.osmosis.core.sort.v0_6.EntityContainerComparator;
import org.openstreetmap.osmosis.core.sort.v0_6.EntitySorter;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import javax.media.jai.*;
import javax.media.jai.registry.RIFRegistry;
import java.awt.*;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;

public class HgtFileReader implements RunnableSource {

    private static final Logger LOG = Logger.getLogger(HgtFileReader.class.getName());

    static {
        try {
            OperationRegistry registry = JAI.getDefaultInstance().getOperationRegistry();
            registry.registerDescriptor(new ContourDescriptor());
            RenderedImageFactory rif = new ContourRIF();
            RIFRegistry.register(registry, "Contour", "org.jaitools.media.jai", rif);
        } catch (Exception ignored) {
        }
    }

    private final File hgtFile;
    private final int interval;
    private final String elevKey;
    private final String contourKey;
    private final String contourVal;
    private final String contourExtKey;
    private final String contourExtMajor;
    private final String contourExtMedium;
    private final String contourExtMinor;

    private int oversampling = 1;
    private int eleMultiply = 1;
    private double rdpDistance = 0.0;

    private int majorEle = 500;
    private int mediumEle = 100;

    private int maxNodesPerWay = 0;

    private Sink sink;

    private Date timestamp;
    private OsmUser osmUser;

    private long wayId;
    private long nodeId;

    private double maxLon;
    private double minLon;
    private double maxLat;
    private double minLat;

    private int pixels; // 1201 or 3601
    private double resolution;
    private AffineTransformation jtsTransformation;

    HgtFileReader(String filePath, int interval, String elevKey, String contourKey, String contourVal, String contourExtKey, String contourExtMajor, String contourExtMedium, String contourExtMinor) {
        this.hgtFile = new File(filePath);
        this.interval = interval;
        this.elevKey = elevKey;
        this.contourKey = contourKey;
        this.contourVal = contourVal;
        this.contourExtKey = contourExtKey;
        this.contourExtMajor = contourExtMajor;
        this.contourExtMedium = contourExtMedium;
        this.contourExtMinor = contourExtMinor;
    }

    @Override
    public void setSink(Sink sink) {
        this.sink = sink;
    }

    private double calcFixFactor() {
        if (oversampling == 1) {
            return 1.0;
        }
        return oversampling + 1.0 / pixels / 2; // ToDo Stimmt das?
    }

    @Override
    public void run() {
        EntitySorter sorter = null;

        try {
            Integer[] words = loadHgtFile();

            LOG.log(Level.INFO, String.format("minLon: %f, maxLon: %f", minLon - resolution / 2, maxLon + resolution / 2));
            LOG.log(Level.INFO, String.format("minLat: %f, maxLat: %f", minLat - resolution / 2, maxLat + resolution / 2));

            PlanarImage tiledImage = buildImage(words);
            if (oversampling != 1) {
                tiledImage = resizeImage(tiledImage, tiledImage.getWidth() * oversampling);
            }

            Collection<LineString> lines = buildContourLines(tiledImage);

            initOsmVariables();

            LOG.log(Level.INFO, "Write to output stream ... BEGIN");

            sorter = new EntitySorter(new EntityContainerComparator(new EntityByTypeThenIdComparator()), false);
            sorter.setSink(sink);
            sorter.initialize(Collections.emptyMap());
            double fixFactor = calcFixFactor();
            final BoundContainer boundContainer = new BoundContainer(
                    new Bound(maxLon + resolution / 2 / fixFactor, minLon - resolution / 2 / fixFactor,
                            maxLat + resolution / 2 / fixFactor, minLat - resolution / 2 / fixFactor, "https://www.benpl.net/thegoat/about.html"));
            sorter.process(boundContainer);

            LOG.log(Level.FINER, "BoundContainer= " + boundContainer.getEntity().toString());

            for (LineString line : lines) {
                int elev = ((Double) line.getUserData()).intValue() / eleMultiply;
                if (elev <= 0 || elev > 9000) {
                    continue;
                }
                Geometry simplified = line;
                if (rdpDistance > 0) {
                    simplified = DouglasPeuckerSimplifier.simplify(line, rdpDistance);
                }
                if (!simplified.isEmpty()) {
                    simplified.apply(jtsTransformation);
                    handleLineString((LineString)simplified, elev, sorter);
                }
            }

            sorter.complete();

            LOG.log(Level.INFO, "Write to output stream ... END");

        } catch (Exception e) {
            throw new Error(e);
        } finally {
            if (sorter != null) sorter.close();
        }
    }

    /**
     * Loads HGT file into buffer, and initializes relevant global variables
     */
    private Integer[] loadHgtFile() throws IOException {
        if (!hgtFile.isFile()) throw new Error("File " + hgtFile.getAbsolutePath() + " not exist");

        String filename = hgtFile.getName().toLowerCase();

        if (!filename.endsWith(".hgt") || filename.length() != 11)
            throw new Error(String.format("File name %s invalid. It should look like [N28E086.hgt].", hgtFile.getName()));

        char ch0 = filename.charAt(0);
        char ch3 = filename.charAt(3);
        minLat = Integer.parseInt(filename.substring(1, 3));
        minLon = Integer.parseInt(filename.substring(4, 7));
        if ((ch0 != 'n' && ch0 != 's') || (ch3 != 'w' && ch3 != 'e') || minLat > 90 || minLon > 180) {
            throw new Error(String.format("File name %s invalid. It should look like [N28E086.hgt].", hgtFile.getName()));
        } else {
            if (ch0 == 's') minLat = -minLat;
            if (ch3 == 'w') minLon = -minLon;
        }

        maxLon = minLon + 1;
        maxLat = minLat + 1;

        int secs;
        byte[] bytes;
        Integer[] words;

        long size = hgtFile.length();
        if (size == (3601 * 3601 * 2)) {
            pixels = 3601;
            secs = 1;
        } else if (size == (1201 * 1201 * 2)) {
            pixels = 1201;
            secs = 3;
        } else {
            throw new Error(hgtFile.getAbsolutePath() + " invalid file size");
        }

        bytes = new byte[pixels * pixels * 2];
        words = new Integer[pixels * pixels];

        resolution = secs / 3600.0;

        //
        // Load HGT file
        //
        LOG.log(Level.INFO, String.format("Load %s ... BEGIN", hgtFile.getAbsolutePath()));

        try (DataInputStream dis = new DataInputStream(new FileInputStream(hgtFile))) {
            dis.readFully(bytes);

            for (int i = 0; i < words.length; i++) {
                words[i] = eleMultiply *  (((bytes[2 * i] << 8) & 0xff00) | (bytes[2 * i + 1] & 0x00ff));
            }
        }

        //
        // GRID TO GEO
        //
        double fixFactor = calcFixFactor();
        jtsTransformation = new AffineTransformation(resolution / fixFactor, 0, minLon, 0,
                -resolution / fixFactor, maxLat);

        LOG.log(Level.INFO, String.format("Load %s ... END", hgtFile.getAbsolutePath()));

        return words;
    }

    /**
     * Converts HGT to tiled image
     */
    private TiledImage buildImage(Integer[] words) {
        LOG.log(Level.INFO, "Convert to tiled image ... BEGIN");
        TiledImage tiledImage = ImageUtils.createImageFromArray(words, pixels, pixels);
        LOG.log(Level.INFO, "Convert to tiled image ... END");

        return tiledImage;
    }

    /**
     * Converts tiled image to contour lines
     */
    private Collection<LineString> buildContourLines(PlanarImage tiledImage) {
        LOG.log(Level.INFO, "Convert to contour lines ... BEGIN");

        ParameterBlockJAI pb = new ParameterBlockJAI("Contour");
        pb.setSource("source0", tiledImage);
        pb.setParameter("band", 0);
        pb.setParameter("simplify", Boolean.TRUE);

        /*List<Integer> levels = Arrays.asList(400 * eleMultiply, 420 * eleMultiply, 440 * eleMultiply,
                460 * eleMultiply, 480 * eleMultiply, 500 * eleMultiply);
        pb.setParameter("levels", levels);*/
        pb.setParameter("interval", interval * eleMultiply);

        pb.setParameter("nodata", Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -32768, 32768 * eleMultiply));

        RenderedOp dest = JAI.create("Contour", pb);

        @SuppressWarnings("unchecked")
        Collection<LineString> lines = (Collection<LineString>) dest.getProperty(ContourDescriptor.CONTOUR_PROPERTY_NAME);
        dest.dispose();

        LOG.log(Level.INFO, "Convert to contour lines ... END");

        return lines;
    }

    /*public static PlanarImage resizeImage(PlanarImage image, int destSize) {
        float scale = (float)destSize / image.getWidth();
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
        RenderingHints renderHints = new RenderingHints(KEY_RENDERING, VALUE_RENDER_QUALITY);
        ParameterBlockJAI pb = new ParameterBlockJAI("Scale", RenderableRegistryMode.MODE_NAME);

        pb.setSource("source0", image);

        pb.setParameter("xScale", scale);
        pb.setParameter("yScale", scale);
        pb.setParameter("xTrans", 0.0F);
        pb.setParameter("yTrans", 0.0F);
        pb.setParameter("interpolation", interpolation);

        RenderableOp dest = JAI.createRenderable("Scale", pb, renderHints);
        PlanarImage resizedImage = (PlanarImage)dest.createDefaultRendering();

        LOG.log(Level.FINER, "Image resized to : " + resizedImage.getWidth() + "x"
                + resizedImage.getHeight());
        return resizedImage;
    }*/

    public static PlanarImage resizeImage(PlanarImage image, int destSize) {
        // See https://github.com/NetLogo/GIS-Extension/issues/4
        System.setProperty("com.sun.media.jai.disableMediaLib", "true");

        float scale = (float)destSize / image.getWidth();
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
        RenderingHints renderHints = new RenderingHints(KEY_RENDERING, VALUE_RENDER_QUALITY);
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

    /**
     * Initializes dummy OSM variables (starts from high numbers to avoid conflict with official OSM ids)
     */
    private void initOsmVariables() {
        long lon = (long) minLon + 180L;
        long lat = (long) minLat + 90L;

        long wayBlockSize = (long) Math.pow(4, 10) * 10;
        long nodeBlockSize = (long) Math.pow(4, 10) * 100;

        wayId = 10000000L + (lon * 180L + lat) * wayBlockSize;
        nodeId = 10000000L + (360L * 180L) * wayBlockSize + (lon * 180L + lat) * nodeBlockSize;

        timestamp = new Date();
        osmUser = new OsmUser(999999, "dummyUser");
    }

    /**
     * Extracts LineString, builds OSM way and writes to output sink
     */
    private void handleLineString(LineString line, Integer elev, Sink sink) {
        if (line.getNumPoints() < 2) {
            // A single point makes no sense
            return;
        }

        Coordinate[] allCoordinates = line.getCoordinates();
        boolean lineIsClosed = line.isClosed();

        List<Coordinate[]> listOfCoordinates = new ArrayList<>();
        if (maxNodesPerWay == 0 || allCoordinates.length < maxNodesPerWay) {
            // No splitting requested or the number of line points is small enough
            listOfCoordinates.add(allCoordinates);
        } else {
            // Split the coordinates array into multiple arrays
            int numberOfSegments = allCoordinates.length / maxNodesPerWay + 1;
            int segmentSize = allCoordinates.length / numberOfSegments + 1;
            int first = 0;
            int last = segmentSize;
            while (first < allCoordinates.length - 1) {
                if (last >= allCoordinates.length) {
                    // The last segment is probably smaller than segmentSize
                    last = allCoordinates.length - 1;
                }
                Coordinate[] coordinates = new Coordinate[last - first + 1];
                System.arraycopy(allCoordinates, first, coordinates, 0, last - first + 1);
                listOfCoordinates.add(coordinates);
                first = last;
                last += segmentSize;
            }

            lineIsClosed = false;
        }

        Node lastNode = null;
        for (Coordinate[] coordinates : listOfCoordinates) {
            List<WayNode> wayNodes = new ArrayList<>(coordinates.length);
            for (int i = 0; i < coordinates.length; i++) {
                if (i == coordinates.length - 1 && lineIsClosed) {
                    // Last point in a closed polygon ==> reuse the first point
                    WayNode wayNode = wayNodes.get(0);
                    wayNodes.add(new WayNode(wayNode.getNodeId(), wayNode.getLatitude(), wayNode.getLongitude()));
                    break;
                }

                Coordinate coordinate = coordinates[i];

                Node osmNode = lastNode;
                if (osmNode == null ||
                        Math.abs(osmNode.getLatitude() - coordinate.y) > 1e-10 ||
                        Math.abs(osmNode.getLongitude() - coordinate.x) > 1e-10) {
                    // Coordinate is different ==> Create new node
                    osmNode = new Node(
                            new CommonEntityData(nodeId++, 1, timestamp, osmUser, 0),
                            coordinate.y,   // latitude
                            coordinate.x);  // longitude
                    sink.process(new NodeContainer(osmNode));
                }

                wayNodes.add(new WayNode(osmNode.getId(), coordinate.y, coordinate.x));
                lastNode = osmNode;
            }

            Way osmWay = new Way(new CommonEntityData(wayId, 1, timestamp, osmUser, 0), wayNodes);
            wayId++;

            osmWay.getTags().add(new Tag(elevKey, elev.toString()));
            osmWay.getTags().add(new Tag(contourKey, contourVal));
            if (elev % majorEle == 0) {
                osmWay.getTags().add(new Tag(contourExtKey, contourExtMajor));
            } else if (elev % mediumEle == 0) {
                osmWay.getTags().add(new Tag(contourExtKey, contourExtMedium));
            } else {
                osmWay.getTags().add(new Tag(contourExtKey, contourExtMinor));
            }

            sink.process(new WayContainer(osmWay));
        }
    }

    public void setOversampling(int oversampling) {
        this.oversampling = oversampling;
    }

    public void setEleMultiply(int eleMultiply) {
        this.eleMultiply = eleMultiply;
    }

    public void setRdpDistance(double rdpDistance) {
        this.rdpDistance = rdpDistance;
    }

    public void setMajorEle(int majorEle) {
        this.majorEle = majorEle;
    }

    public void setMediumEle(int mediumEle) {
        this.mediumEle = mediumEle;
    }

    public void setMaxNodesPerWay(int maxNodesPerWay) {
        this.maxNodesPerWay = mediumEle;
    }
}
