package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for connected components construction in SliceMerger.
 */
public class SliceMergerConnectedComponentsTest {
    
    private SliceMerger merger;
    
    @Before
    public void setUp() {
        merger = new SliceMerger();
    }
    
    @Test
    public void testMergeSlices_SimpleOverlap() {
        // Create two 3x3 slices with overlapping regions
        ImagePlus slice1 = createSlice(3, 3);
        ImagePlus slice2 = createSlice(3, 3);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();
        
        // Slice1: region 1 in top-left corner
        proc1.putPixel(0, 0, 1);
        proc1.putPixel(1, 0, 1);
        proc1.putPixel(0, 1, 1);
        
        // Slice2: region 2 overlaps with region 1
        proc2.putPixel(0, 0, 2);
        proc2.putPixel(1, 0, 2);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 2 slices", 2, result.getNSlices());
        
        // Check that overlapping regions have the same label
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        
        int label1 = resultProc1.getPixel(0, 0);
        int label2 = resultProc2.getPixel(0, 0);
        
        assertTrue("Labels should be positive", label1 > 0 && label2 > 0);
        assertEquals("Overlapping regions should have same label", label1, label2);
    }
    
    @Test
    public void testMergeSlices_NoOverlap() {
        // Create two 3x3 slices with non-overlapping regions
        ImagePlus slice1 = createSlice(3, 3);
        ImagePlus slice2 = createSlice(3, 3);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();
        
        // Slice1: region 1 in top-left corner
        proc1.putPixel(0, 0, 1);
        proc1.putPixel(1, 0, 1);
        
        // Slice2: region 2 in bottom-right corner (no overlap)
        proc2.putPixel(2, 2, 2);
        proc2.putPixel(1, 2, 2);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 2 slices", 2, result.getNSlices());
        
        // Check that non-overlapping regions have different labels
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        
        int label1 = resultProc1.getPixel(0, 0);
        int label2 = resultProc2.getPixel(2, 2);
        
        assertTrue("Labels should be positive", label1 > 0 && label2 > 0);
        assertNotEquals("Non-overlapping regions should have different labels", label1, label2);
    }
    
    @Test
    public void testMergeSlices_TransitiveOverlap() {
        // Create three slices with transitive overlap: A overlaps B, B overlaps C
        ImagePlus slice1 = createSlice(3, 3);
        ImagePlus slice2 = createSlice(3, 3);
        ImagePlus slice3 = createSlice(3, 3);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();
        ByteProcessor proc3 = (ByteProcessor) slice3.getProcessor();
        
        // Slice1: region A
        proc1.putPixel(0, 0, 1);
        proc1.putPixel(1, 0, 1);
        
        // Slice2: region B overlaps with A
        proc2.putPixel(0, 0, 2);
        proc2.putPixel(1, 1, 2);
        
        // Slice3: region C overlaps with B
        proc3.putPixel(1, 1, 3);
        proc3.putPixel(2, 1, 3);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2, slice3);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 3 slices", 3, result.getNSlices());
        
        // Check that all transitively connected regions have the same label
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        ImageProcessor resultProc3 = result.getStack().getProcessor(3);
        
        int labelA = resultProc1.getPixel(0, 0);
        int labelB = resultProc2.getPixel(0, 0);
        int labelC = resultProc3.getPixel(1, 1);
        
        assertTrue("Labels should be positive", labelA > 0 && labelB > 0 && labelC > 0);
        assertEquals("Transitively connected regions should have same label (A-B)", labelA, labelB);
        assertEquals("Transitively connected regions should have same label (B-C)", labelB, labelC);
    }
    
    @Test
    public void testMergeSlices_EmptySlices() {
        // Test with all empty slices
        ImagePlus slice1 = createSlice(3, 3);
        ImagePlus slice2 = createSlice(3, 3);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 2 slices", 2, result.getNSlices());
        
        // Check that all pixels are background (0)
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals("All pixels should be background", 0, resultProc1.getPixel(x, y));
                assertEquals("All pixels should be background", 0, resultProc2.getPixel(x, y));
            }
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testMergeSlices_NullInput() {
        merger.mergeSlices(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testMergeSlices_EmptyList() {
        merger.mergeSlices(Arrays.asList());
    }
    
    private ImagePlus createSlice(int width, int height) {
        ByteProcessor proc = new ByteProcessor(width, height);
        return new ImagePlus("slice", proc);
    }
}