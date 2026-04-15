package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

public class SliceSegmenterTest {

    @Test
    public void testSegmentAllSlices_validStack() {
        // Create a simple 3D stack with 3 slices
        int width = 10, height = 10;
        ImageStack stack = new ImageStack(width, height);
        
        // Add 3 slices with different patterns
        for (int z = 0; z < 3; z++) {
            ShortProcessor sp = new ShortProcessor(width, height);
            // Create a simple pattern with high values in center
            for (int y = 3; y < 7; y++) {
                for (int x = 3; x < 7; x++) {
                    sp.set(x, y, 1000 + z * 100); // Different intensity per slice
                }
            }
            stack.addSlice("slice_" + (z + 1), sp);
        }
        
        ImagePlus stack3D = new ImagePlus("test_stack", stack);
        stack3D.setDimensions(1, 3, 1); // 1 channel, 3 slices, 1 frame
        
        // Test the segmentation with basic parameters
        SliceSegmenter segmenter = new SliceSegmenter();
        List<ImagePlus> results = segmenter.segmentAllSlices(stack3D, 500, 10.0);
        
        // Verify results
        assertNotNull("Results should not be null", results);
        assertEquals("Should have 3 results", 3, results.size());
        
        // Check each result
        for (int i = 0; i < results.size(); i++) {
            ImagePlus result = results.get(i);
            assertNotNull("Result " + i + " should not be null", result);
            assertEquals("Result " + i + " should have correct width", width, result.getWidth());
            assertEquals("Result " + i + " should have correct height", height, result.getHeight());
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSegmentAllSlices_nullInput() {
        SliceSegmenter segmenter = new SliceSegmenter();
        segmenter.segmentAllSlices(null, 500, 10.0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSegmentAllSlices_insufficientSlices() {
        // Create a stack with only 1 slice
        ShortProcessor sp = new ShortProcessor(10, 10);
        ImagePlus singleSlice = new ImagePlus("single", sp);

        SliceSegmenter segmenter = new SliceSegmenter();
        segmenter.segmentAllSlices(singleSlice, 500, 10.0);
    }
    
    @Test
    public void testEmptySliceHandling() {
        // Create a 3D stack where one slice will have no seeds
        int width = 10, height = 10;
        ImageStack stack = new ImageStack(width, height);
        
        // First slice: has high values (should find seeds)
        ShortProcessor sp1 = new ShortProcessor(width, height);
        for (int y = 3; y < 7; y++) {
            for (int x = 3; x < 7; x++) {
                sp1.set(x, y, 1000);
            }
        }
        stack.addSlice("slice_1", sp1);
        
        // Second slice: all low values (should find no seeds)
        ShortProcessor sp2 = new ShortProcessor(width, height);
        for (int i = 0; i < width * height; i++) {
            sp2.set(i, 50); // Very low values
        }
        stack.addSlice("slice_2", sp2);
        
        ImagePlus stack3D = new ImagePlus("test_stack", stack);
        stack3D.setDimensions(1, 2, 1);
        
        // Test with high thresholds
        SliceSegmenter segmenter = new SliceSegmenter();
        List<ImagePlus> results = segmenter.segmentAllSlices(stack3D, 500, 10.0);
        
        assertNotNull("Results should not be null", results);
        assertEquals("Should have 2 results", 2, results.size());
        
        // First slice should have some segmentation
        ImagePlus result1 = results.get(0);
        assertNotNull("First result should not be null", result1);
        
        // Second slice should be empty (all zeros)
        ImagePlus result2 = results.get(1);
        assertNotNull("Second result should not be null", result2);
        
        // Check that second slice is indeed all zeros
        int[] pixels = (int[]) result2.getProcessor().getPixels();
        boolean allZeros = true;
        for (int pixel : pixels) {
            if (pixel != 0) {
                allZeros = false;
                break;
            }
        }
        assertTrue("Second slice should be all zeros (no seeds found)", allZeros);
    }
}