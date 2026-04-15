package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for overlap detection methods in SliceMerger.
 */
public class SliceMergerOverlapTest {

    private SliceMerger merger;

    @Before
    public void setUp() {
        merger = new SliceMerger();
    }

    @Test
    public void testCalculateAllOverlaps() throws Exception {
        // Create two 3x3 slices with multiple regions
        ImagePlus slice1 = new ImagePlus("slice1", new ByteProcessor(3, 3));
        ImagePlus slice2 = new ImagePlus("slice2", new ByteProcessor(3, 3));

        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();

        // Slice1: two regions
        proc1.putPixel(0, 0, 1); // Region 1
        proc1.putPixel(1, 0, 1);
        proc1.putPixel(2, 0, 2); // Region 2
        proc1.putPixel(2, 1, 2);

        // Slice2: two regions with overlaps
        proc2.putPixel(0, 0, 3); // Region 3 overlaps with region 1
        proc2.putPixel(1, 0, 3);
        proc2.putPixel(2, 0, 4); // Region 4 overlaps with region 2

        Map<Integer, Map<Integer, Integer>> overlaps = invokeCalculateAllOverlaps(slice1, slice2);

        // Verify overlap matrix
        assertNotNull("Should have overlaps for region 1", overlaps.get(1));
        assertNotNull("Should have overlaps for region 2", overlaps.get(2));

        assertEquals("Region 1 should overlap with region 3 by 2 pixels",
                     2, overlaps.get(1).get(3).intValue());
        assertEquals("Region 2 should overlap with region 4 by 1 pixel",
                     1, overlaps.get(2).get(4).intValue());
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Map<Integer, Integer>> invokeCalculateAllOverlaps(ImagePlus slice1, ImagePlus slice2) throws Exception {
        Method method = SliceMerger.class.getDeclaredMethod("calculateAllOverlaps",
            ImagePlus.class, ImagePlus.class);
        method.setAccessible(true);
        return (Map<Integer, Map<Integer, Integer>>) method.invoke(merger, slice1, slice2);
    }
}
