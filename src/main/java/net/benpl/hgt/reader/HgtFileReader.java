package net.benpl.hgt.reader;

import ch.bubendorf.hgt.ContourOpImage;
import ch.bubendorf.hgt.ImageUtil;
import org.jaitools.media.jai.contour.ContourDescriptor;
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

import javax.media.jai.JAI;
import javax.media.jai.OperationRegistry;
import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;
import java.awt.image.Raster;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

//import org.jaitools.media.jai.contour.ContourDescriptor;
//import org.jaitools.media.jai.contour.ContourRIF;

// https://github.com/eclipse/imagen verwenden!
public class HgtFileReader implements RunnableSource {

    private static final Logger LOG = Logger.getLogger(HgtFileReader.class.getName());

    static {
        try {
            OperationRegistry registry = JAI.getDefaultInstance().getOperationRegistry();
//            registry.registerDescriptor(new ContourDescriptor());
//            RenderedImageFactory rif = new ContourRIF();
//            RIFRegistry.register(registry, "Contour", "org.jaitools.media.jai", rif);
        } catch (Exception ignored) {
        }
    }

    private final File hgtFile;
    private int interval;
    private String levels;
    private String elevKey;
    private String contourKey;
    private String contourVal;
    private String contourExtKey;
    private String contourExtMajor;
    private String contourExtMedium;
    private String contourExtMinor;

    private double oversampling = 1;
    private int eleMultiply = 1;
    private int eleOffset = 0;
    private double rdpDistance = 0.0;
    private int flatThreshold;

    private int majorEle = 500;
    private int mediumEle = 100;

    private int maxNodesPerWay = 0;
    private int minNodesPerWay = 4;
    private int minNodesPerOpenWay = 12;

    private Sink sink;

    private Date timestamp;
    private OsmUser osmUser;

    private long wayId;
    private long wayCount = 0;

    private long nodeId;
    private long nodeCount = 0;

    private double maxLon;
    private double minLon;
    private double maxLat;
    private double minLat;

    private boolean writeContourLines = true;
    private boolean writeHgtNodes = false;
    private boolean writeRasterNodes = false;

    private int pixels; // 1201 or 3601 (Auch bei Verwendung von oversampling)
    private double resolution;
    private AffineTransformation jtsTransformation;

    HgtFileReader(String filePath) {
        hgtFile = new File(filePath);
    }

    @Override
    public void setSink(Sink sink) {
        this.sink = sink;
    }

    @Override
    public void run() {
        EntitySorter sorter = null;

        try {
            Integer[] words = loadAndInitHgtFile();

            LOG.log(Level.INFO, String.format("minLon: %f, maxLon: %f", minLon - resolution / 2, maxLon + resolution / 2));
            LOG.log(Level.INFO, String.format("minLat: %f, maxLat: %f", minLat - resolution / 2, maxLat + resolution / 2));

            PlanarImage tiledImage = ImageUtil.buildImage(words, pixels);
            initOsmVariables();
            sorter = new EntitySorter(new EntityContainerComparator(new EntityByTypeThenIdComparator()), false);
            sorter.setSink(sink);
            sorter.initialize(Collections.emptyMap());
//            double fixFactor = calcFixFactor();
            final BoundContainer boundContainer = new BoundContainer(
                    new Bound(maxLon + resolution / 2, minLon - resolution / 2,
                            maxLat + resolution / 2, minLat - resolution / 2, "https://www.benpl.net/thegoat/about.html"));
            LOG.log(Level.FINER, "BoundContainer= " + boundContainer.getEntity().toString());
            sorter.process(boundContainer);

            if (writeHgtNodes && !writeRasterNodes) {
                // DEBUG: Add the HGT Points to the output
                LOG.log(Level.INFO, "Write HGT nodes ... BEGIN");
                double lonStep = (maxLon - minLon) / (pixels - 1);
                double latStep = (maxLat - minLat) / (pixels - 1);
                for (int y = 0; y < pixels; y++) {
                    double latitude = minLat + y * latStep;
                    for (int x = 0; x < pixels; x++) {
                        double longitude = minLon + x * lonStep;
                        Node osmNode = new Node(
                                new CommonEntityData(nodeId++, 1, timestamp, osmUser, 0),
                                latitude, longitude);
                        nodeCount++;
                        int index = pixels * (pixels - y - 1) + x;
                        int ele = (words[index] - eleOffset) / eleMultiply;
                        osmNode.getTags().add(new Tag(elevKey, Integer.toString(ele)));
                        sink.process(new NodeContainer(osmNode));
                    }
                }
                LOG.log(Level.INFO, "Write HGT nodes ... END");
            }
            words = null; // forget the data!

            /*if (oversampling != 1.0) {
                LOG.log(Level.INFO, "Oversampling ... BEGIN");
                PlanarImage orginalImage = tiledImage;
                tiledImage = ImageUtil.resizeImage(orginalImage, (int) ((orginalImage.getWidth() - minusEins ) * oversampling) + minusEins);
                orginalImage.dispose();
                LOG.log(Level.INFO, "Image resized to : " + tiledImage.getWidth() + "x" + tiledImage.getHeight());
                LOG.log(Level.INFO, "Oversampling ... END");
            }*/

            if (flatThreshold >= 0) {
                // Eliminate flat surfaces by setting its elevation to NODATA
                LOG.log(Level.INFO, "Flat Surface Elimination ... BEGIN");
                int numberOfFlatPoints = 0;
                Raster sourceRaster = tiledImage.getData();
                TiledImage noFlatImage = new TiledImage(tiledImage, false);
                for (int y = 0; y < tiledImage.getHeight(); y++) {
                    for (int x = 0; x < tiledImage.getWidth(); x++) {
                        int value = sourceRaster.getSample(x, y, 0);
                        if (y > 1 && x > 1 && y < tiledImage.getWidth() - 1 && x < tiledImage.getHeight() - 1) {
                            int vNorth = sourceRaster.getSample(x, y - 1, 0);
                            int vNorthWest = sourceRaster.getSample(x - 1, y - 1, 0);
                            int vWest = sourceRaster.getSample(x - 1, y, 0);
                            int vSouthWest = sourceRaster.getSample(x - 1, y + 1, 0);
                            int vSouth = sourceRaster.getSample(x, y + 1, 0);
                            int vSouthEast = sourceRaster.getSample(x + 1, y + 1, 0);
                            int vEast = sourceRaster.getSample(x + 1, y, 0);
                            int vNorthEast = sourceRaster.getSample(x + 1, y - 1, 0);

                            if (Math.abs(vNorth - value) <= flatThreshold &&
                                    Math.abs(vWest - value) <= flatThreshold &&
                                    Math.abs(vEast - value) <= flatThreshold &&
                                    Math.abs(vSouth - value) <= flatThreshold &&
                                    Math.abs(vNorthWest - value) <= flatThreshold &&
                                    Math.abs(vNorthEast - value) <= flatThreshold &&
                                    Math.abs(vSouthWest - value) <= flatThreshold &&
                                    Math.abs(vSouthEast - value) <= flatThreshold) {
//                                System.out.println("x=" + x + ", y=" + y + ", value=" + value);
//                                value = -32768 * eleMultiply + eleOffset;
                                value = Integer.MIN_VALUE;
                                numberOfFlatPoints++;
                            }
                        }
                        noFlatImage.setSample(x, y, 0, value);
                    }
                }
                tiledImage = noFlatImage;
                LOG.log(Level.INFO, "Flat Surface Elimination ... END");
                int percent = (int) Math.round(100.0 * numberOfFlatPoints / pixels / pixels);
                LOG.log(Level.FINER, "Number of flat points: " + numberOfFlatPoints + " (" + percent + "%)");
            }

            if (oversampling != 1.0) {
                LOG.log(Level.INFO, "Oversampling ... BEGIN");
                PlanarImage orginalImage = tiledImage;
                tiledImage = ImageUtil.resizeImage(orginalImage, (int) ((orginalImage.getWidth() - 1) * oversampling) + 1);
                orginalImage.dispose();
                LOG.log(Level.INFO, "Image resized to : " + tiledImage.getWidth() + "x" + tiledImage.getHeight());
                LOG.log(Level.INFO, "Oversampling ... END");
            }

            if (writeRasterNodes) {
                // DEBUG: Add the tiledImage to the output
                LOG.log(Level.INFO, "Write HGT and Raster nodes ... BEGIN");
                int imageWidth = tiledImage.getWidth();
                final Raster imageData = tiledImage.getData();
                double lonStep = (maxLon - minLon) / (imageWidth - 1);
                double latStep = (maxLat - minLat) / (imageWidth - 1);
                for (int y = 0; y < imageWidth; y++) {
                    double latitude = minLat + y * latStep;
                    for (int x = 0; x < imageWidth; x++) {
                        double longitude = minLon + x * lonStep;
                        Node osmNode = new Node(
                                new CommonEntityData(nodeId++, 1, timestamp, osmUser, 0),
                                latitude, longitude);
                        nodeCount++;
                        double ele = (imageData.getSampleDouble(x, (imageWidth - y - 1), 0) - eleOffset) / eleMultiply;
                        osmNode.getTags().add(new Tag(elevKey, Double.toString(ele)));
                        sink.process(new NodeContainer(osmNode));
                    }
                }
                LOG.log(Level.INFO, "Write HGT and Raster nodes ... END");
            }

            if (writeContourLines) {
                Collection<LineString> lines = buildContourLinesNeu(tiledImage);
                LOG.log(Level.INFO, "Write contour lines to output stream ... BEGIN");
                for (LineString line : lines) {
                    final double v = ((Double) line.getUserData()).doubleValue();
                    int elev = (int)(v / eleMultiply);
                    if (elev <= 0 || elev > 9000) {
                        continue;
                    }
                    Geometry simplified = line;
                    if (rdpDistance > 0) {
                        // Simplify the lines using the Douglas-Peucker algorithm
                        simplified = DouglasPeuckerSimplifier.simplify(line, rdpDistance);
                    }
                    if (!simplified.isEmpty()) {
                        simplified.apply(jtsTransformation);
                        handleLineString((LineString) simplified, elev, sorter);
                    }
                }
                LOG.log(Level.INFO, "Write to output stream ... END");
                LOG.log(Level.FINE, "Number of written nodes: " + nodeCount);
                LOG.log(Level.FINE, "Number of written ways: " + wayCount);
            }
            sorter.complete();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new Error(e);
        } finally {
            if (sorter != null) {
                sorter.close();
            }
        }
    }

    /**
     * Loads HGT file into buffer, and initializes relevant global variables
     */
    private Integer[] loadAndInitHgtFile() throws IOException {
        if (!hgtFile.isFile()) {
            throw new Error("File " + hgtFile.getAbsolutePath() + " not exist");
        }

        String filename = hgtFile.getName().toLowerCase();

        if (!filename.endsWith(".hgt") || filename.length() != 11) {
            throw new Error(String.format("File name %s invalid. It should look like [N47E006.hgt].", hgtFile.getName()));
        }
        char ch0 = filename.charAt(0);
        char ch3 = filename.charAt(3);
        minLat = Integer.parseInt(filename.substring(1, 3));
        minLon = Integer.parseInt(filename.substring(4, 7));
        if ((ch0 != 'n' && ch0 != 's') || (ch3 != 'w' && ch3 != 'e') || minLat > 90 || minLon > 180) {
            throw new Error(String.format("File name %s invalid. It should look like [N46E007.hgt].", hgtFile.getName()));
        } else {
            if (ch0 == 's') {
                minLat = -minLat;
            }
            if (ch3 == 'w') {
                minLon = -minLon;
            }
        }

        maxLon = minLon + 1;
        maxLat = minLat + 1;

        long size = hgtFile.length();
        int seconds;
        if (size == (3601 * 3601 * 2)) {
            pixels = 3601;
            seconds = 1;
        } else if (size == (1201 * 1201 * 2)) {
            pixels = 1201;
            seconds = 3;
        } else {
            throw new Error(hgtFile.getAbsolutePath() + " invalid file size");
        }

        double f = (2.0 - oversampling) / 3600.0 / oversampling;
        resolution = seconds / 3600.0 / oversampling / (1.0 + f);

        //
        // Load HGT file
        //
        LOG.log(Level.INFO, String.format("Load %s ... BEGIN", hgtFile.getAbsolutePath()));
        Integer[] words = loadFile();

        //
        // GRID TO GEO
        //
        jtsTransformation = new AffineTransformation(resolution, 0, minLon, 0, -resolution, maxLat);

        LOG.log(Level.INFO, String.format("Load %s ... END", hgtFile.getAbsolutePath()));

        return words;
    }

    private Integer[] loadFile() throws IOException {
        byte[] bytes = new byte[pixels * pixels * 2];
        Integer[] words = new Integer[pixels * pixels];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(hgtFile))) {
            dis.readFully(bytes);

            for (int i = 0; i < words.length; i++) {
                words[i] = eleMultiply * (((bytes[2 * i] << 8) & 0xff00) | (bytes[2 * i + 1] & 0x00ff)) + eleOffset;
            }
        }
        return words;
    }

    /**
     * Converts tiled image to contour lines
     */
    /*private Collection<LineString> buildContourLinesAlt(PlanarImage tiledImage) {
        LOG.log(Level.INFO, "Convert to contour lines ... BEGIN");

        ParameterBlockJAI pb = new ParameterBlockJAI("Contour");
        pb.setSource("source0", tiledImage);
        pb.setParameter("band", 0);
        pb.setParameter("simplify", Boolean.TRUE);
        pb.setParameter("strictNodata", Boolean.FALSE);

        if (levels != null && levels.length() > 0) {
            List<Integer> levelInts = Arrays.stream(levels.split(","))
                    .map(l -> Integer.parseInt(l) * eleMultiply)
                    .collect(Collectors.toList());
            pb.setParameter("levels", levelInts);
        } else {
            pb.setParameter("interval", interval * eleMultiply);
        }

        pb.setParameter("nodata", Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE, -32768, 32768 * eleMultiply + eleOffset));

        RenderedOp dest = JAI.create("Contour", pb);

        @SuppressWarnings("unchecked")
        Collection<LineString> lines = (Collection<LineString>) dest.getProperty(ContourDescriptor.CONTOUR_PROPERTY_NAME);
        dest.dispose();

        LOG.log(Level.INFO, "Convert to contour lines ... END");
        LOG.log(Level.FINE, "Number of produced lines: " + lines.size());

        return lines;
    }*/

    private Collection<LineString> buildContourLinesNeu(PlanarImage tiledImage) {
        LOG.log(Level.INFO, "Convert to contour lines ... BEGIN");

        List<Integer> levelInts = null;
        if (levels != null && levels.length() > 0) {
            levelInts = Arrays.stream(levels.split(","))
                    .map(l -> Integer.parseInt(l) * eleMultiply)
                    .collect(Collectors.toList());
        }
        final Collection<Object> noDatas = Arrays.asList(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MIN_VALUE,
                -32768, -32768 * eleMultiply + eleOffset);
        ContourOpImage contourOpImage = new ContourOpImage(tiledImage, null, 0, levelInts,
                (double)interval * eleMultiply, noDatas, false, true, false);

        @SuppressWarnings("unchecked")
        Collection<LineString> lines = (Collection<LineString>) contourOpImage.getProperty(ContourDescriptor.CONTOUR_PROPERTY_NAME);

        LOG.log(Level.INFO, "Convert to contour lines ... END");
        LOG.log(Level.FINE, "Number of produced lines: " + lines.size());

        return lines;
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
        if (line.getNumPoints() < minNodesPerWay) {
            // A single point or only a few make no sense
            return;
        }
        if (!line.isClosed() && line.getNumPoints() < minNodesPerOpenWay) {
            // Komisches Zeuchs ==> Weg damit
//            System.out.println("Offene Linie mit " + line.getNumPoints() + " Punkten verworfen!");
            return;
        }

        Coordinate[] allCoordinates = line.getCoordinates();
        boolean lineIsClosed = line.isClosed();

        List<Coordinate[]> listOfCoordinates = new ArrayList<>();
//        System.out.println("ele=" + elev + ", Number of coordinates=" + allCoordinates.length);
        if (maxNodesPerWay == 0 || allCoordinates.length <= maxNodesPerWay) {
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
//        System.out.println("ele=" + elev + ", Number of coordinates=" + coordinates.length);
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
                    nodeCount++;
                    sink.process(new NodeContainer(osmNode));
                }

                wayNodes.add(new WayNode(osmNode.getId(), coordinate.y, coordinate.x));
                lastNode = osmNode;
            }

            Way osmWay = new Way(new CommonEntityData(wayId, 1, timestamp, osmUser, 0), wayNodes);
            wayId++;
            wayCount++;

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

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public void setElevKey(String elevKey) {
        this.elevKey = elevKey;
    }

    public void setContourKey(String contourKey) {
        this.contourKey = contourKey;
    }

    public void setContourVal(String contourVal) {
        this.contourVal = contourVal;
    }

    public void setContourExtKey(String contourExtKey) {
        this.contourExtKey = contourExtKey;
    }

    public void setContourExtMajor(String contourExtMajor) {
        this.contourExtMajor = contourExtMajor;
    }

    public void setContourExtMedium(String contourExtMedium) {
        this.contourExtMedium = contourExtMedium;
    }

    public void setContourExtMinor(String contourExtMinor) {
        this.contourExtMinor = contourExtMinor;
    }

    public void setLevels(String levels) {
        this.levels = levels;
    }

    public void setOversampling(double oversampling) {
        this.oversampling = oversampling;
    }

    public void setEleMultiply(int eleMultiply) {
        this.eleMultiply = eleMultiply;
    }

    public void setEleOffset(int eleOffset) {
        this.eleOffset = eleOffset;
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
        this.maxNodesPerWay = maxNodesPerWay;
    }

    public void setWriteContourLines(boolean writeContourLines) {
        this.writeContourLines = writeContourLines;
    }

    public void setWriteHgtNodes(boolean writeHgtNodes) {
        this.writeHgtNodes = writeHgtNodes;
    }

    public void setWriteRasterNodes(boolean writeRasterNodes) {
        this.writeRasterNodes = writeRasterNodes;
    }

    public void setFlatThreshold(int flatThreshold) {
        this.flatThreshold = flatThreshold;
    }

    public void setMinNodesPerWay(int minNodesPerWay) {
        this.minNodesPerWay = minNodesPerWay;
    }

    public void setMinNodesPerOpenWay(int minNodesPerOpenWay) {
        this.minNodesPerOpenWay = minNodesPerOpenWay;
    }
}
