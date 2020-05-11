package net.benpl.hgt.reader;

import org.openstreetmap.osmosis.core.pipeline.common.TaskConfiguration;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManager;
import org.openstreetmap.osmosis.core.pipeline.common.TaskManagerFactory;
import org.openstreetmap.osmosis.core.pipeline.v0_6.RunnableSourceManager;

class HgtFileReaderFactory extends TaskManagerFactory {
    @Override
    protected TaskManager createTaskManagerImpl(TaskConfiguration taskConfig) {
        String filePath = getStringArgument(taskConfig, "file");
        int interval = getIntegerArgument(taskConfig, "interval", 20);
        String levels = getStringArgument(taskConfig, "levels", "");

        String elevKey = getStringArgument(taskConfig, "elev-key", "ele");
        String contourKey = getStringArgument(taskConfig, "contour-key", "contour");
        String contourVal = getStringArgument(taskConfig, "contour-val", "elevation");
        String contourExtKey = getStringArgument(taskConfig, "contour-ext-key", "contour_ext");
        String contourExtMajor = getStringArgument(taskConfig, "contour-ext-major", "elevation_major");
        String contourExtMedium = getStringArgument(taskConfig, "contour-ext-medium", "elevation_medium");
        String contourExtMinor = getStringArgument(taskConfig, "contour-ext-minor", "elevation_minor");

        double oversampling = getDoubleArgument(taskConfig, "oversampling", 1.0);
        int elevationMultiply = getIntegerArgument(taskConfig, "elevation-factor", 1);
        int elevationOffset = getIntegerArgument(taskConfig, "elevation-offset", 0);
        double rdpDistance = getDoubleArgument(taskConfig, "simplify-contours-epsilon", 0);
        int majorElevation = getIntegerArgument(taskConfig, "major", 500);
        int mediumElevation = getIntegerArgument(taskConfig, "medium", 100);
        int maxNodesPerWay = getIntegerArgument(taskConfig, "max-nodes-per-way", 0);
        int minNodesPerWay = getIntegerArgument(taskConfig, "min-nodes-per-way", 4);
        int minNodesPerOpenWay = getIntegerArgument(taskConfig, "min-nodes-per-open-way", 12);
        int flatThreshold = getIntegerArgument(taskConfig, "flat-threshold", -1);

        boolean writeContourLines = getBooleanArgument(taskConfig, "write-contour-ways", true);
        boolean writeHgtNodes = getBooleanArgument(taskConfig, "write-hgt-nodes", false);
        boolean writeRasterNodes = getBooleanArgument(taskConfig, "write-raster-nodes", false);

        HgtFileReader task = new HgtFileReader(filePath);
        task.setInterval(interval);
        task.setElevKey(elevKey);
        task.setContourKey(contourKey);
        task.setContourVal(contourVal);
        task.setContourExtKey(contourExtKey);
        task.setContourExtMajor(contourExtMajor);
        task.setContourExtMedium(contourExtMedium);
        task.setContourExtMinor(contourExtMinor);
        task.setLevels(levels);
        task.setOversampling(oversampling);
        task.setEleMultiply(elevationMultiply);
        task.setEleOffset(elevationOffset);
        task.setRdpDistance(rdpDistance);
        task.setMajorEle(majorElevation);
        task.setMediumEle(mediumElevation);
        task.setMaxNodesPerWay(maxNodesPerWay);
        task.setMinNodesPerWay(minNodesPerWay);
        task.setMinNodesPerOpenWay(minNodesPerOpenWay);
        task.setWriteContourLines(writeContourLines);
        task.setWriteHgtNodes(writeHgtNodes);
        task.setWriteRasterNodes(writeRasterNodes);
        task.setFlatThreshold(flatThreshold);

        return new RunnableSourceManager(taskConfig.getId(), task, taskConfig.getPipeArgs());
    }
}
