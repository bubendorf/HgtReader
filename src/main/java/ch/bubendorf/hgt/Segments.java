package ch.bubendorf.hgt;

import org.locationtech.jts.geom.LineString;

import java.util.*;

/**
 * A container for the segments collected by ContourOpImage.
 * It will return them as merged lines eventually applying simplification procedures
 *
 * @author Andrea Aime - GeoSolutions
 * @since 1.1
 * @version $Id$
 */
class Segments {
    boolean simplify;

    Segment temp = new Segment();

    /**
     * List of segments sorted by start element
     */
    TreeSet<Segment> startList = new TreeSet<>(Segment.START_COMPARATOR);

    /**
     * List of segments sorted by end element
     */
    TreeSet<Segment> endList = new TreeSet<>(Segment.END_COMPARATOR);

    /**
     * The completed segment list
     */
    List<LineString> result = new ArrayList<>();

    public Segments(boolean simplify) {
        this.simplify = simplify;
    }

    /**
     * Adds a segment to the mix
     * @param x1 x1
     * @param y1 y1
     * @param x2 x2
     * @param y2 y2
     */
    public void add(double x1, double y1, double x2, double y2) {
        // we don't add single points, only full segments
        if (Segment.samePoint(x1, y1, x2, y2)) {
            return;
        }

        // keep the lower ordinate first, it's the one that can connect with
        // previous segments
        if (y2 < y1) {
            double tmp = y1;
            y1 = y2;
            y2 = tmp;
            tmp = x1;
            x1 = x2;
            x2 = tmp;
        }

        // try to add using the lowest point first
        if (appendSegment(x1, y1, x2, y2)) {
            return;
        }

        // no connection, need to create a new segment
        Segment segment = new Segment(x1, y1, x2, y2, simplify);
        startList.add(segment);
        endList.add(segment);
    }

    private boolean appendSegment(double x1, double y1, double x2, double y2) {
        temp.setXY(x1, y1, x1, y1);
        Segment segment = search(startList, temp);
        if (segment != null) {
            addToStartList(x2, y2, segment);
            return true;
        } else {
            segment = search(endList, temp);
            if (segment != null) {
                addToEndList(x2, y2, segment);
                return true;
            }
        }
        temp.setXY(x2, y2, x2, y2);
        segment = search(startList, temp);
        if (segment != null) {
            addToStartList(x1, y1, segment);
            return true;
        } else {
            segment = search(endList, temp);
            if (segment != null) {
                addToEndList(x1, y1, segment);
                return true;
            }
        }
        return false;
    }

    private void addToEndList(double x, double y, Segment segment) {
        // add the point
        endList.remove(segment);
        segment.addAfterEnd(x, y);
        // can we merge the segment to another in the end list now?
        // (we cannot keep two segments with the same endpoint in the treeset, they would be
        // considered equal)
        temp.setXY(x, y, x, y);
        Segment mergeTarget = search(endList, segment);
        if (mergeTarget == null) {
            // add back in the end list
            endList.add(segment);
        } else {
            while (mergeTarget != null) {
                // this segment is getting merged, removed it also from the start list
                startList.remove(segment);
                // update the merged target
                endList.remove(mergeTarget);
                mergeTarget.merge(segment, Segment.MergePoint.END_END);
                mergeTarget.touched = true;
                // is the merge going to cascade to another segment now that
                // we have modified the end point of s2 (and made it become the
                // start point of "segment"?
                Segment next = search(endList, mergeTarget);
                if (next != null) {
                    segment = mergeTarget;
                    mergeTarget = next;
                } else {
                    // no next, we can add back the merge target to the start list
                    endList.add(mergeTarget);
                    mergeTarget = null;
                }
            }
        }
        assert listConsistent() : "Start: " + startList + "\nEnd: " + endList;
    }

    private void addToStartList(double x, double y, Segment segment) {
        assert listConsistent() : "Start: " + startList + "\nEnd: " + endList;
        // add the point
        startList.remove(segment);
        segment.addBeforeStart(x, y);
        // can we merge the segment to another in the start list now?
        temp.setXY(x, y, x, y);
        Segment mergeTarget = search(startList, segment);
        if (mergeTarget == null) {
            // add back in the start list
            startList.add(segment);
        } else {
            while (mergeTarget != null) {
                // this segment is getting merged, removed it also from the end list
                endList.remove(segment);
                // update the merge target
                startList.remove(mergeTarget);
                mergeTarget.merge(segment, Segment.MergePoint.START_START);
                mergeTarget.touched = true;
                // is the merge going to cascade to another segment now that
                // we have modified the start point of s2 (and made it become the
                // end point of "segment"?
                Segment next = search(startList, mergeTarget);
                if (next != null) {
                    segment = mergeTarget;
                    mergeTarget = next;
                } else {
                    // no next, we can add back the merge target to the start list
                    startList.add(mergeTarget);
                    mergeTarget = null;
                }
            }
        }
        assert listConsistent() : "Start: " + startList + "\nEnd: " + endList;
    }

    /**
     * Informs the segments a new scanline is started
     */
    public void lineComplete() {
        // look for all the segments that have not been touched during the last scan
        for (Segment segment : new ArrayList<>(startList)) {
            // if touched, we can continue using it
            if (segment.touched) {
                segment.touched = false;
                continue;
            }

            // if not, remove it from the search lists
            startList.remove(segment);
            endList.remove(segment);

            // can we merge it with an existing one?
            temp.setXY(segment.xStart, segment.yStart, segment.xStart, segment.yStart);
            Segment.MergePoint mergePoint = null;

            // end-start is the most efficient merge we can make, try it first
            Segment mergeTarget = search(endList, temp);
            if (mergeTarget != null) {
                mergePoint = Segment.MergePoint.END_START;
            } else {
                mergeTarget = search(startList, temp);
                if (mergeTarget != null) {
                    mergePoint = Segment.MergePoint.START_START;
                } else {
                    temp.setXY(segment.xEnd, segment.yEnd, segment.xEnd, segment.yEnd);
                    mergeTarget = search(startList, temp);
                    if (mergeTarget != null) {
                        mergePoint = Segment.MergePoint.START_END;
                    } else {
                        mergeTarget = search(endList, temp);
                        if (mergeTarget != null) {
                            mergePoint = Segment.MergePoint.END_END;
                        }
                    }
                }
            }

            if (mergeTarget != null) {
                startList.remove(mergeTarget);
                endList.remove(mergeTarget);
                mergeTarget.merge(segment, mergePoint);
                startList.add(mergeTarget);
                endList.add(mergeTarget);
            } else {
                LineString ls = segment.toLineString();
                result.add(ls);
            }

            assert listConsistent() : "Start: " + startList + "\nEnd: " + endList;
        }
    }

    private boolean listConsistent() {
        Set<Segment> start = new HashSet<>(startList);
        Set<Segment> end = new HashSet<>(endList);
        return start.containsAll(end) && end.containsAll(start);
    }

    Segment search(TreeSet<Segment> list, Segment reference) {
        Segment segment = list.ceiling(reference);
        if (segment == null) {
            return null;
        }
        if (list == startList) {
            if (Segment.samePoint(segment.xStart, segment.yStart, reference.xStart,
                    reference.yStart)) {
                return segment;
            }
        } else {
            if (Segment.samePoint(segment.xEnd, segment.yEnd, reference.xEnd, reference.yEnd)) {
                return segment;
            }
        }

        return null;
    }

    /**
     * Returns the merged and eventually simplified segments
     *
     * @return Merged Segments
     */
    public List<LineString> getMergedSegments() {
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Segments(").append(startList.size()).append(",")
                .append(result.size()).append(") ");
        sb.append("active=");
        for (Segment segment : startList) {
            sb.append(segment).append("\n");
        }
        sb.append("complete=");
        for (LineString ls : result) {
            sb.append(ls).append("\n");
        }
        return sb.toString();
    }

}
