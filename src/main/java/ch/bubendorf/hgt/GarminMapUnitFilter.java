package ch.bubendorf.hgt;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;

/**
 * Ein CoordinateSequenceFilter welcher die Koordinaten im WGS84 auf die Auflösung
 * eines Garmin Navigationsgerätes rundet.
 * Diese ist 360° / 2^24 = 0.00002146°
 */
public class GarminMapUnitFilter implements CoordinateSequenceFilter {
    @Override
    public void filter(CoordinateSequence seq, int i) {
        double lon = seq.getOrdinate(i, 0);
        double lat = seq.getOrdinate(i, 1);

        int lonMapUnit = Utils.toMapUnit(lon);
        int latMapUnit = Utils.toMapUnit(lat);

        double lon2 = Utils.toDegrees(lonMapUnit);
        double lat2 = Utils.toDegrees(latMapUnit);

        seq.setOrdinate(i, 0, lon2);
        seq.setOrdinate(i, 1, lat2);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean isGeometryChanged() {
        return true;
    }
}
