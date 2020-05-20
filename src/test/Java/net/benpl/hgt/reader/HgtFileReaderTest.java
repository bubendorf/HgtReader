package net.benpl.hgt.reader;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.misc.v0_6.NullWriter;
import org.openstreetmap.osmosis.core.progress.v0_6.EntityProgressLogger;

public class HgtFileReaderTest {

    @Tag("ignore")
    @Test
    public void run() {
        HgtFileReader task = new HgtFileReader("C:\\Geo\\GCRouter\\data\\dem\\04-NASA\\N47E007.hgt");
        final NullWriter nullWriter = new NullWriter();
        final EntityProgressLogger progressLogger = new EntityProgressLogger(1000, "UnitTest") {
            public void process(EntityContainer entityContainer) {
                super.process(entityContainer);
            }
        };
        progressLogger.setSink(nullWriter);
//        task.setSink(nullWriter); // Forget the output!
        task.setSink(progressLogger); // Log the output

        //        task.setInterval(20);
        task.setLevels("400,450,500,550,600");
        task.setElevKey("ele");
        task.setContourKey("contour");
        task.setContourVal("elevation");
        task.setContourExtKey("contour_ext");
        task.setContourExtMajor("elevation_major");
        task.setContourExtMedium("elevation_medium");
        task.setContourExtMinor("elevation_minor");
        task.setOversampling(1);
        task.setEleMultiply(1000);
        task.setEleOffset(0);
        task.setRdpDistance(1);
        task.setMajorEle(200);
        task.setMediumEle(100);
        task.setMaxNodesPerWay(1000);
        task.setMinNodesPerWay(8);
        task.setMinNodesPerOpenWay(12);
        task.setWriteContourLines(true);
        task.setWriteHgtNodes(false);
        task.setWriteRasterNodes(false);
        task.setFlatThreshold(-1);
        task.setGarminMapUnits(true);

        task.run();
    }
}
