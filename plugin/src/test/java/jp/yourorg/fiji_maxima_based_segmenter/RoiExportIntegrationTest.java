package jp.yourorg.fiji_maxima_based_segmenter;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import jp.yourorg.fiji_maxima_based_segmenter.roi.RoiExporter3D;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration test for ROI export functionality in Slice-Based 3D Segmenter.
 * 
 * This test verifies that the RoiExporter3D integration works correctly
 * and that 3D segmentation results are properly exported to the ROI Manager.
 */
public class RoiExportIntegrationTest {
    
    @Before
    public void setUp() {
        // Clear ROI Manager before each test
        RoiManager rm = RoiManager.getRoiManager();
        rm.reset();
    }
    
    @Test
    public void testRoiExportIntegration() {
        // Create a simple 3D label image with known regions
        ImageStack stack = new ImageStack(10, 10);
        
        // Slice 1: Single region (label 1)
        ByteProcessor slice1 = new ByteProcessor(10, 10);
        for (int x = 2; x < 8; x++) {
            for (int y = 2; y < 8; y++) {
                slice1.putPixel(x, y, 1);
            }
        }
        stack.addSlice("slice1", slice1);
        
        // Slice 2: Two regions (labels 1 and 2)
        ByteProcessor slice2 = new ByteProcessor(10, 10);
        for (int x = 2; x < 6; x++) {
            for (int y = 2; y < 6; y++) {
                slice2.putPixel(x, y, 1);
            }
        }
        for (int x = 6; x < 9; x++) {
            for (int y = 6; y < 9; y++) {
                slice2.putPixel(x, y, 2);
            }
        }
        stack.addSlice("slice2", slice2);
        
        ImagePlus labelImage = new ImagePlus("test_labels", stack);
        
        // Test direct ROI export
        RoiExporter3D exporter = new RoiExporter3D();
        exporter.exportToRoiManager(labelImage);
        
        // Verify ROIs were created
        RoiManager rm = RoiManager.getRoiManager();
        int roiCount = rm.getCount();
        
        assertTrue("ROI Manager should contain exported ROIs", roiCount > 0);
        System.out.println("ROI export test passed - " + roiCount + " ROIs created");
        
        // Verify ROI names follow expected pattern
        boolean hasValidNames = false;
        for (int i = 0; i < roiCount; i++) {
            String name = rm.getName(i);
            if (name.matches("obj-\\d{3}-z\\d{3}")) {
                hasValidNames = true;
                break;
            }
        }
        assertTrue("ROIs should have valid naming pattern (obj-XXX-zXXX)", hasValidNames);
    }
    
    @Test
    public void testSegmentApiWithRoiExport() {
        // Create a simple 3D test image
        ImageStack stack = new ImageStack(10, 10);
        
        // Create two slices with bright regions that should be segmented
        for (int z = 0; z < 2; z++) {
            ByteProcessor slice = new ByteProcessor(10, 10);
            // Background
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    slice.putPixel(x, y, 30);
                }
            }
            // Bright region in center
            for (int x = 3; x < 7; x++) {
                for (int y = 3; y < 7; y++) {
                    slice.putPixel(x, y, 200);
                }
            }
            stack.addSlice("slice" + (z + 1), slice);
        }
        
        ImagePlus testImage = new ImagePlus("test_image", stack);
        
        // Clear ROI Manager
        RoiManager rm = RoiManager.getRoiManager();
        rm.reset();
        
        // Run segmentation with ROI export
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            testImage,
            50,   // bg_threshold
            2.0   // tolerance
        );
        
        // Verify segmentation succeeded
        assertNotNull("Segmentation should succeed", result);
        
        // Verify ROIs were exported
        int roiCount = rm.getCount();
        assertTrue("ROI Manager should contain exported ROIs after segmentation", roiCount > 0);
        
        System.out.println("Segment API with ROI export test passed - " + roiCount + " ROIs created");
    }
}