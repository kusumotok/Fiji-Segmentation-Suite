package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for LabelReassigner class.
 * 
 * Tests the core functionality of label reassignment including:
 * - Basic reassignment to consecutive integers
 * - Background pixel preservation
 * - Empty image handling
 * - Single label handling
 * - Multiple slices handling
 */
public class LabelReassignerTest {
    
    private LabelReassigner reassigner;
    
    @Before
    public void setUp() {
        reassigner = new LabelReassigner();
    }
    
    @Test
    public void testReassignLabels_NullInput_ThrowsException() {
        try {
            reassigner.reassignLabels(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
    
    @Test
    public void testReassignLabels_EmptyImage_ReturnsEmptyImage() {
        // Create empty 3D image (all zeros)
        ImageStack stack = new ImageStack(10, 10);
        stack.addSlice(new ByteProcessor(10, 10)); // All zeros
        stack.addSlice(new ByteProcessor(10, 10)); // All zeros
        ImagePlus emptyImage = new ImagePlus("Empty", stack);
        
        ImagePlus result = reassigner.reassignLabels(emptyImage);
        
        assertNotNull(result);
        assertEquals(10, result.getWidth());
        assertEquals(10, result.getHeight());
        assertEquals(2, result.getNSlices());
        
        // Verify all pixels are still 0
        for (int z = 1; z <= 2; z++) {
            result.setSlice(z);
            ImageProcessor proc = result.getProcessor();
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    assertEquals(0, proc.getPixel(x, y));
                }
            }
        }
    }
    
    @Test
    public void testReassignLabels_SingleSlice_ConsecutiveLabels() {
        // Create single slice with labels [0, 5, 0, 12, 5, 0, 7]
        ByteProcessor proc = new ByteProcessor(7, 1);
        proc.putPixel(0, 0, 0);  // background
        proc.putPixel(1, 0, 5);  // should become 1
        proc.putPixel(2, 0, 0);  // background
        proc.putPixel(3, 0, 12); // should become 3
        proc.putPixel(4, 0, 5);  // should become 1
        proc.putPixel(5, 0, 0);  // background
        proc.putPixel(6, 0, 7);  // should become 2
        
        ImageStack stack = new ImageStack(7, 1);
        stack.addSlice(proc);
        ImagePlus image = new ImagePlus("Test", stack);
        
        ImagePlus result = reassigner.reassignLabels(image);
        
        assertNotNull(result);
        assertEquals(7, result.getWidth());
        assertEquals(1, result.getHeight());
        assertEquals(1, result.getNSlices());
        
        // Verify reassigned labels
        result.setSlice(1);
        ImageProcessor resultProc = result.getProcessor();
        assertEquals(0, resultProc.getPixel(0, 0)); // background stays 0
        assertEquals(1, resultProc.getPixel(1, 0)); // 5 -> 1 (smallest non-zero)
        assertEquals(0, resultProc.getPixel(2, 0)); // background stays 0
        assertEquals(3, resultProc.getPixel(3, 0)); // 12 -> 3 (largest)
        assertEquals(1, resultProc.getPixel(4, 0)); // 5 -> 1 (same as before)
        assertEquals(0, resultProc.getPixel(5, 0)); // background stays 0
        assertEquals(2, resultProc.getPixel(6, 0)); // 7 -> 2 (middle)
    }
    
    @Test
    public void testReassignLabels_MultipleSlices_ConsecutiveLabels() {
        // Create 3D image with 2 slices
        // Slice 1: [0, 10, 0, 20]
        // Slice 2: [30, 0, 10, 0]
        // Expected mapping: 10->1, 20->2, 30->3
        
        ByteProcessor slice1 = new ByteProcessor(4, 1);
        slice1.putPixel(0, 0, 0);
        slice1.putPixel(1, 0, 10);
        slice1.putPixel(2, 0, 0);
        slice1.putPixel(3, 0, 20);
        
        ByteProcessor slice2 = new ByteProcessor(4, 1);
        slice2.putPixel(0, 0, 30);
        slice2.putPixel(1, 0, 0);
        slice2.putPixel(2, 0, 10);
        slice2.putPixel(3, 0, 0);
        
        ImageStack stack = new ImageStack(4, 1);
        stack.addSlice(slice1);
        stack.addSlice(slice2);
        ImagePlus image = new ImagePlus("Test3D", stack);
        
        ImagePlus result = reassigner.reassignLabels(image);
        
        assertNotNull(result);
        assertEquals(4, result.getWidth());
        assertEquals(1, result.getHeight());
        assertEquals(2, result.getNSlices());
        
        // Verify slice 1: [0, 1, 0, 2]
        result.setSlice(1);
        ImageProcessor proc1 = result.getProcessor();
        assertEquals(0, proc1.getPixel(0, 0));
        assertEquals(1, proc1.getPixel(1, 0)); // 10 -> 1
        assertEquals(0, proc1.getPixel(2, 0));
        assertEquals(2, proc1.getPixel(3, 0)); // 20 -> 2
        
        // Verify slice 2: [3, 0, 1, 0]
        result.setSlice(2);
        ImageProcessor proc2 = result.getProcessor();
        assertEquals(3, proc2.getPixel(0, 0)); // 30 -> 3
        assertEquals(0, proc2.getPixel(1, 0));
        assertEquals(1, proc2.getPixel(2, 0)); // 10 -> 1
        assertEquals(0, proc2.getPixel(3, 0));
    }
    
    @Test
    public void testReassignLabels_SingleLabel_BecomesOne() {
        // Create image with only one non-zero label (42)
        ByteProcessor proc = new ByteProcessor(3, 3);
        proc.putPixel(0, 0, 0);
        proc.putPixel(1, 0, 42);
        proc.putPixel(2, 0, 0);
        proc.putPixel(0, 1, 42);
        proc.putPixel(1, 1, 0);
        proc.putPixel(2, 1, 42);
        proc.putPixel(0, 2, 0);
        proc.putPixel(1, 2, 0);
        proc.putPixel(2, 2, 0);
        
        ImageStack stack = new ImageStack(3, 3);
        stack.addSlice(proc);
        ImagePlus image = new ImagePlus("SingleLabel", stack);
        
        ImagePlus result = reassigner.reassignLabels(image);
        
        assertNotNull(result);
        result.setSlice(1);
        ImageProcessor resultProc = result.getProcessor();
        
        // All 42s should become 1s, 0s stay 0s
        assertEquals(0, resultProc.getPixel(0, 0));
        assertEquals(1, resultProc.getPixel(1, 0));
        assertEquals(0, resultProc.getPixel(2, 0));
        assertEquals(1, resultProc.getPixel(0, 1));
        assertEquals(0, resultProc.getPixel(1, 1));
        assertEquals(1, resultProc.getPixel(2, 1));
        assertEquals(0, resultProc.getPixel(0, 2));
        assertEquals(0, resultProc.getPixel(1, 2));
        assertEquals(0, resultProc.getPixel(2, 2));
    }
    
    @Test
    public void testReassignLabels_AlreadyConsecutive_Unchanged() {
        // Create image with already consecutive labels [0, 1, 2, 3]
        ByteProcessor proc = new ByteProcessor(4, 1);
        proc.putPixel(0, 0, 0);
        proc.putPixel(1, 0, 1);
        proc.putPixel(2, 0, 2);
        proc.putPixel(3, 0, 3);
        
        ImageStack stack = new ImageStack(4, 1);
        stack.addSlice(proc);
        ImagePlus image = new ImagePlus("Consecutive", stack);
        
        ImagePlus result = reassigner.reassignLabels(image);
        
        assertNotNull(result);
        result.setSlice(1);
        ImageProcessor resultProc = result.getProcessor();
        
        // Should remain unchanged
        assertEquals(0, resultProc.getPixel(0, 0));
        assertEquals(1, resultProc.getPixel(1, 0));
        assertEquals(2, resultProc.getPixel(2, 0));
        assertEquals(3, resultProc.getPixel(3, 0));
    }
    
    @Test
    public void testReassignLabels_LargeGaps_BecomesConsecutive() {
        // Create image with large gaps: [0, 100, 0, 200, 0, 50]
        // Using values within ByteProcessor range (0-255)
        ByteProcessor proc = new ByteProcessor(6, 1);
        proc.putPixel(0, 0, 0);
        proc.putPixel(1, 0, 100);
        proc.putPixel(2, 0, 0);
        proc.putPixel(3, 0, 200);
        proc.putPixel(4, 0, 0);
        proc.putPixel(5, 0, 50);
        
        ImageStack stack = new ImageStack(6, 1);
        stack.addSlice(proc);
        ImagePlus image = new ImagePlus("LargeGaps", stack);
        
        ImagePlus result = reassigner.reassignLabels(image);
        
        assertNotNull(result);
        result.setSlice(1);
        ImageProcessor resultProc = result.getProcessor();
        
        // Expected mapping: 50->1, 100->2, 200->3
        assertEquals(0, resultProc.getPixel(0, 0));
        assertEquals(2, resultProc.getPixel(1, 0)); // 100 -> 2
        assertEquals(0, resultProc.getPixel(2, 0));
        assertEquals(3, resultProc.getPixel(3, 0)); // 200 -> 3
        assertEquals(0, resultProc.getPixel(4, 0));
        assertEquals(1, resultProc.getPixel(5, 0)); // 50 -> 1
    }
}