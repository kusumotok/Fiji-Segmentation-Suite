package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests specifically for 3D label image generation in SliceMerger.
 * 
 * This test verifies that:
 * - Labels are reassigned to consecutive integers starting from 1
 * - Background pixels remain 0
 * - Connected regions across slices get the same final label
 * - Non-connected regions get different labels
 * - The 3D ImagePlus has correct dimensions and format
 */
public class SliceMerger3DLabelTest {
    
    private SliceMerger merger;
    
    @Before
    public void setUp() {
        merger = new SliceMerger();
    }
    
    @Test
    public void testConsecutiveLabelAssignment() {
        // Create 3 slices with known regions
        ImagePlus slice1 = createSlice(5, 5);
        ImagePlus slice2 = createSlice(5, 5);
        ImagePlus slice3 = createSlice(5, 5);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();
        ByteProcessor proc3 = (ByteProcessor) slice3.getProcessor();
        
        // Slice 1: Two separate regions (labels 1 and 2)
        proc1.putPixel(0, 0, 1);
        proc1.putPixel(1, 0, 1);
        proc1.putPixel(4, 4, 2);
        
        // Slice 2: One region overlapping with region 1, one new region
        proc2.putPixel(0, 0, 3);  // Overlaps with region 1
        proc2.putPixel(2, 2, 4);  // New region
        
        // Slice 3: One region overlapping with region 2, one overlapping with new region
        proc3.putPixel(4, 4, 5);  // Overlaps with region 2
        proc3.putPixel(2, 2, 6);  // Overlaps with new region
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2, slice3);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        // Verify basic properties
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 3 slices", 3, result.getNSlices());
        assertEquals("Result should have correct width", 5, result.getWidth());
        assertEquals("Result should have correct height", 5, result.getHeight());
        
        // Collect all unique labels from the result
        Set<Integer> allLabels = new HashSet<>();
        for (int z = 1; z <= 3; z++) {
            ImageProcessor resultProc = result.getStack().getProcessor(z);
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    int label = resultProc.getPixel(x, y);
                    if (label > 0) {
                        allLabels.add(label);
                    }
                }
            }
        }
        
        // Verify consecutive labeling
        int maxLabel = allLabels.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertEquals("Should have consecutive labels from 1 to max", maxLabel, allLabels.size());
        
        for (int i = 1; i <= maxLabel; i++) {
            assertTrue("Should contain label " + i, allLabels.contains(i));
        }
        
        // Verify connected regions have same labels
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        ImageProcessor resultProc3 = result.getStack().getProcessor(3);
        
        // Region 1 (slice1, 0,0) should connect to region 3 (slice2, 0,0)
        int label1_0_0 = resultProc1.getPixel(0, 0);
        int label2_0_0 = resultProc2.getPixel(0, 0);
        assertEquals("Connected regions should have same label", label1_0_0, label2_0_0);
        
        // Region 2 (slice1, 4,4) should NOT connect to region 5 (slice3, 4,4) 
        // because there's no region at (4,4) in slice2 (the middle slice)
        int label1_4_4 = resultProc1.getPixel(4, 4);
        int label3_4_4 = resultProc3.getPixel(4, 4);
        assertNotEquals("Non-adjacent regions should have different labels", label1_4_4, label3_4_4);
        
        // Region 4 (slice2, 2,2) should connect to region 6 (slice3, 2,2)
        int label2_2_2 = resultProc2.getPixel(2, 2);
        int label3_2_2 = resultProc3.getPixel(2, 2);
        assertEquals("Connected regions should have same label", label2_2_2, label3_2_2);
    }
    
    @Test
    public void testBackgroundPixelsRemainZero() {
        // Create slices with some regions and background
        ImagePlus slice1 = createSlice(4, 4);
        ImagePlus slice2 = createSlice(4, 4);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc2 = (ByteProcessor) slice2.getProcessor();
        
        // Only set a few pixels as foreground
        proc1.putPixel(1, 1, 1);
        proc2.putPixel(1, 1, 2);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        // Check that most pixels are background (0)
        int backgroundCount = 0;
        int foregroundCount = 0;
        
        for (int z = 1; z <= 2; z++) {
            ImageProcessor resultProc = result.getStack().getProcessor(z);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    int label = resultProc.getPixel(x, y);
                    if (label == 0) {
                        backgroundCount++;
                    } else {
                        foregroundCount++;
                    }
                }
            }
        }
        
        assertEquals("Should have 2 foreground pixels", 2, foregroundCount);
        assertEquals("Should have 30 background pixels", 30, backgroundCount);
    }
    
    @Test
    public void testEmptySlicesHandling() {
        // Create slices where some are empty
        ImagePlus slice1 = createSlice(3, 3);
        ImagePlus slice2 = createSlice(3, 3); // This will be empty
        ImagePlus slice3 = createSlice(3, 3);
        
        ByteProcessor proc1 = (ByteProcessor) slice1.getProcessor();
        ByteProcessor proc3 = (ByteProcessor) slice3.getProcessor();
        
        // Only set pixels in slice1 and slice3
        proc1.putPixel(1, 1, 1);
        proc3.putPixel(1, 1, 2);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2, slice3);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 3 slices", 3, result.getNSlices());
        
        // Check that middle slice is all zeros
        ImageProcessor resultProc2 = result.getStack().getProcessor(2);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                assertEquals("Empty slice should have all background pixels", 
                           0, resultProc2.getPixel(x, y));
            }
        }
        
        // Check that slice1 and slice3 have different labels (no connection)
        ImageProcessor resultProc1 = result.getStack().getProcessor(1);
        ImageProcessor resultProc3 = result.getStack().getProcessor(3);
        
        int label1 = resultProc1.getPixel(1, 1);
        int label3 = resultProc3.getPixel(1, 1);
        
        assertTrue("Labels should be positive", label1 > 0 && label3 > 0);
        assertNotEquals("Non-connected regions should have different labels", label1, label3);
    }
    
    @Test
    public void testAllEmptySlices() {
        // Create all empty slices
        ImagePlus slice1 = createSlice(2, 2);
        ImagePlus slice2 = createSlice(2, 2);
        
        List<ImagePlus> sliceLabels = Arrays.asList(slice1, slice2);
        
        ImagePlus result = merger.mergeSlices(sliceLabels);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have 2 slices", 2, result.getNSlices());
        
        // Check that all pixels are background
        for (int z = 1; z <= 2; z++) {
            ImageProcessor resultProc = result.getStack().getProcessor(z);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    assertEquals("All pixels should be background", 
                               0, resultProc.getPixel(x, y));
                }
            }
        }
    }
    
    private ImagePlus createSlice(int width, int height) {
        ByteProcessor proc = new ByteProcessor(width, height);
        return new ImagePlus("slice", proc);
    }
}