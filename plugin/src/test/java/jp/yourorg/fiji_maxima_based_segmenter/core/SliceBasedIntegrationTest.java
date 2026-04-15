package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ShortProcessor;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration test for basic slice-based 3D segmentation functionality.
 * 
 * This test verifies that the core components work together correctly:
 * - SliceSegmenter can process 3D stacks
 * - UnionFind can manage connected components
 * - MergeStatistics can track processing information
 * 
 * This serves as a checkpoint test for Task 4.
 */
public class SliceBasedIntegrationTest {
    
    @Test
    public void testBasicSliceProcessingWorkflow() {
        // Create a simple 3D stack with known structure
        int width = 20, height = 20, nSlices = 3;
        ImageStack stack = new ImageStack(width, height);
        
        // Create slices with overlapping regions
        for (int z = 0; z < nSlices; z++) {
            ShortProcessor sp = new ShortProcessor(width, height);
            
            // Create a central bright region that varies slightly per slice
            int centerX = 10, centerY = 10;
            int radius = 4;
            
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        // Distance from center
                        double dist = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                        if (dist <= radius) {
                            sp.set(x, y, 1000 + z * 50); // Bright region with slight variation per slice
                        }
                    }
                }
            }
            
            stack.addSlice("slice_" + (z + 1), sp);
        }
        
        ImagePlus stack3D = new ImagePlus("test_integration_stack", stack);
        stack3D.setDimensions(1, nSlices, 1);
        
        // Test slice segmentation with parameters that should detect the regions
        SliceSegmenter segmenter = new SliceSegmenter();
        List<ImagePlus> sliceResults = segmenter.segmentAllSlices(stack3D, 500, 10.0);
        
        // Verify basic properties
        assertNotNull("Slice results should not be null", sliceResults);
        assertEquals("Should have results for all slices", nSlices, sliceResults.size());
        
        // Check that each slice has the correct dimensions
        for (int i = 0; i < sliceResults.size(); i++) {
            ImagePlus result = sliceResults.get(i);
            assertNotNull("Result " + i + " should not be null", result);
            assertEquals("Result " + i + " should have correct width", width, result.getWidth());
            assertEquals("Result " + i + " should have correct height", height, result.getHeight());
        }
        
        // Test UnionFind functionality with a simple scenario
        UnionFind uf = new UnionFind(10);
        uf.union(0, 1);
        uf.union(2, 3);
        uf.union(1, 2); // This should connect groups {0,1} and {2,3}
        
        // Verify connected components
        assertEquals("Elements 0 and 3 should be connected", uf.find(0), uf.find(3));
        assertNotEquals("Element 4 should be separate", uf.find(0), uf.find(4));
        
        // Test MergeStatistics creation
        MergeStatistics stats = new MergeStatistics(nSlices, 6, 2, 1500);
        assertEquals("Should track correct number of slices", nSlices, stats.totalSlices);
        assertEquals("Should track 2D regions", 6, stats.total2DRegions);
        assertEquals("Should track 3D regions", 2, stats.total3DRegions);
        assertEquals("Should track processing time", 1500, stats.processingTimeMs);
        
        String summary = stats.toSummaryString();
        assertNotNull("Summary should not be null", summary);
        assertTrue("Summary should contain slice count", summary.contains("3"));
        assertTrue("Summary should contain processing time", summary.contains("1.50"));
        
        System.out.println("Integration test passed - basic slice processing works correctly");
        System.out.println("Statistics summary:\n" + summary);
    }
    
    @Test
    public void testEmptySliceHandlingInWorkflow() {
        // Create a 3D stack where middle slice will be empty
        int width = 10, height = 10;
        ImageStack stack = new ImageStack(width, height);
        
        // First slice: bright region
        ShortProcessor sp1 = new ShortProcessor(width, height);
        for (int y = 3; y < 7; y++) {
            for (int x = 3; x < 7; x++) {
                sp1.set(x, y, 1000);
            }
        }
        stack.addSlice("slice_1", sp1);
        
        // Second slice: all low values (should be empty)
        ShortProcessor sp2 = new ShortProcessor(width, height);
        for (int i = 0; i < width * height; i++) {
            sp2.set(i, 50); // Very low values
        }
        stack.addSlice("slice_2", sp2);
        
        // Third slice: bright region again
        ShortProcessor sp3 = new ShortProcessor(width, height);
        for (int y = 3; y < 7; y++) {
            for (int x = 3; x < 7; x++) {
                sp3.set(x, y, 1000);
            }
        }
        stack.addSlice("slice_3", sp3);
        
        ImagePlus stack3D = new ImagePlus("test_empty_slice_stack", stack);
        stack3D.setDimensions(1, 3, 1);
        
        // Test with high thresholds
        SliceSegmenter segmenter = new SliceSegmenter();
        List<ImagePlus> results = segmenter.segmentAllSlices(stack3D, 500, 10.0);
        
        assertEquals("Should have 3 results", 3, results.size());
        
        // Check that middle slice is empty (all zeros)
        ImagePlus middleResult = results.get(1);
        int[] pixels = (int[]) middleResult.getProcessor().getPixels();
        boolean allZeros = true;
        for (int pixel : pixels) {
            if (pixel != 0) {
                allZeros = false;
                break;
            }
        }
        assertTrue("Middle slice should be all zeros (empty)", allZeros);
        
        System.out.println("Empty slice handling test passed");
    }
}