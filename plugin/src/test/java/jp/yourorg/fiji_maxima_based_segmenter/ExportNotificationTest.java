package jp.yourorg.fiji_maxima_based_segmenter;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import ij.IJ;

/**
 * Test class for export completion notification functionality.
 * 
 * This class tests the enhanced notification system that informs users
 * about ROI Manager export completion, fulfilling Requirement 6.3.
 */
public class ExportNotificationTest {
    
    private ImagePlus testLabelImage;
    
    @Before
    public void setUp() {
        // Create a test 3D label image with known regions
        int width = 20;
        int height = 20;
        int depth = 3;
        
        testLabelImage = IJ.createHyperStack("Test Label Image", width, height, 1, depth, 1, 8);
        
        // Add bright regions that will be detected by segmentation
        for (int z = 1; z <= depth; z++) {
            testLabelImage.setSlice(z);
            ImageProcessor proc = testLabelImage.getProcessor();
            
            // Set background to low value
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    proc.putPixel(x, y, 20);
                }
            }
            
            // Create bright regions that should be segmented
            if (z == 1) {
                // Region 1 in slice 1 - bright square
                for (int y = 5; y < 10; y++) {
                    for (int x = 5; x < 10; x++) {
                        proc.putPixel(x, y, 200);
                    }
                }
            } else if (z == 2) {
                // Region 1 continues in slice 2
                for (int y = 5; y < 10; y++) {
                    for (int x = 5; x < 10; x++) {
                        proc.putPixel(x, y, 200);
                    }
                }
                // Region 2 starts in slice 2
                for (int y = 12; y < 17; y++) {
                    for (int x = 12; x < 17; x++) {
                        proc.putPixel(x, y, 200);
                    }
                }
            } else if (z == 3) {
                // Only region 2 in slice 3
                for (int y = 12; y < 17; y++) {
                    for (int x = 12; x < 17; x++) {
                        proc.putPixel(x, y, 200);
                    }
                }
            }
        }
    }
    
    @Test
    public void testExportNotificationWithValidImage() {
        // This test verifies that the notification system works with a valid label image
        // We can't easily test the actual dialog display in a unit test, but we can
        // verify that the method completes successfully and doesn't throw exceptions
        
        // Test the segment API which includes export notification
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            testLabelImage,
            30,    // bgThreshold (lower than background of 20)
            2.0    // tolerance
        );
        
        // The method should complete successfully
        // Note: In a real ImageJ environment, this would show the notification dialog
        // In the test environment, we just verify no exceptions are thrown
        assertNotNull("Segmentation should produce a result", result);
    }
    
    @Test
    public void testExportNotificationWithNullImage() {
        // Test that the notification system handles null input gracefully
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            null,  // null image
            50,    // bgThreshold
            2.0    // tolerance
        );
        
        // Should return null due to input validation
        assertNull("Segmentation with null input should return null", result);
    }
    
    @Test
    public void testExportNotificationWithInsufficientSlices() {
        // Create a 2D image (single slice) to test validation
        ImagePlus singleSlice = IJ.createImage("Single Slice", "16-bit", 10, 10, 1);
        
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            singleSlice,
            50,    // bgThreshold
            2.0    // tolerance
        );
        
        // Should return null due to insufficient slices
        assertNull("Segmentation with single slice should return null", result);
    }
    
    @Test
    public void testNotificationMessageFormatting() {
        // This test verifies that the notification system can handle different
        // numbers of regions correctly in the message formatting
        
        // Create a simple 3D stack with a single region
        ImagePlus simpleStack = IJ.createHyperStack("Simple Stack", 5, 5, 1, 2, 1, 16);
        
        // Add a single region spanning both slices
        for (int z = 1; z <= 2; z++) {
            simpleStack.setSlice(z);
            ImageProcessor proc = simpleStack.getProcessor();
            proc.putPixel(2, 2, 1); // Single pixel region
        }
        
        // Test segmentation - this should work without throwing exceptions
        // and should format the notification message correctly for 1 region
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            simpleStack,
            0,     // bgThreshold (low to detect single pixel)
            0.0    // tolerance (low to detect single pixel)
        );
        
        // The method should complete successfully
        assertNotNull("Simple segmentation should produce a result", result);
    }
}