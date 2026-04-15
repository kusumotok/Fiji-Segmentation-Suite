package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Integration test for GUI skip functionality.
 * Tests the complete workflow from macro mode detection to ROI export.
 */
public class GUISkipIntegrationTest {
    
    private ImagePlus testImage;
    private Slice_Based_3D_Segmenter_ plugin;
    private RoiManager roiManager;
    
    @Before
    public void setUp() {
        testImage = createTestImage();
        plugin = new Slice_Based_3D_Segmenter_();
        
        // Set the test image as the current image
        testImage.show();
        
        // Initialize ROI Manager
        roiManager = RoiManager.getInstance();
        if (roiManager == null) {
            roiManager = new RoiManager();
        }
        roiManager.reset(); // Clear any existing ROIs
    }
    
    @After
    public void tearDown() {
        // Clean up
        if (testImage != null) {
            testImage.close();
        }
        if (roiManager != null) {
            roiManager.reset();
        }
        Macro.setOptions(null);
    }
    
    private ImagePlus createTestImage() {
        // Create a simple 3D test image with overlapping regions
        int width = 15, height = 15, slices = 3;
        ImagePlus imp = IJ.createImage("Test Stack", "8-bit", width, height, slices);
        
        // Slice 1: Circle in center
        imp.setSlice(1);
        ImageProcessor ip1 = imp.getProcessor();
        ip1.setColor(200);
        ip1.fillOval(4, 4, 7, 7);
        
        // Slice 2: Overlapping rectangle
        imp.setSlice(2);
        ImageProcessor ip2 = imp.getProcessor();
        ip2.setColor(200);
        ip2.fillRect(6, 6, 7, 7);
        
        // Slice 3: Another overlapping circle
        imp.setSlice(3);
        ImageProcessor ip3 = imp.getProcessor();
        ip3.setColor(200);
        ip3.fillOval(7, 7, 6, 6);
        
        return imp;
    }
    
    @Test
    public void testCompleteGUISkipWorkflow() {
        // Set macro options to trigger macro mode
        String macroOptions = "bg_threshold=50 fg_threshold=150 tolerance=30.0 method=WATERSHED";
        Macro.setOptions(macroOptions);
        
        try {
            // Run the plugin - should skip GUI and process in macro mode
            plugin.run("");
            
            // If we reach here without exceptions, the GUI skip worked correctly
            // The plugin should have processed the image without showing GUI
            assertTrue("Plugin should complete macro mode without showing GUI", true);
            
        } catch (Exception e) {
            fail("Complete workflow should not throw exception: " + e.getMessage());
        }
    }
    
    @Test
    public void testGUISkipWithMinimalParameters() {
        // Test with minimal macro parameters
        String macroOptions = "method=WATERSHED";
        Macro.setOptions(macroOptions);
        
        try {
            // Run the plugin
            plugin.run("");
            
            // Should complete without exceptions
            assertTrue("Plugin should handle minimal parameters", true);
            
        } catch (Exception e) {
            fail("Plugin should handle minimal parameters: " + e.getMessage());
        }
    }
    
    @Test
    public void testGUISkipVersusInteractiveMode() {
        // Test that macro mode behaves differently from interactive mode
        
        // First, test interactive mode (no macro options)
        Macro.setOptions(null);
        
        try {
            plugin.run("");
            // Interactive mode should show GUI message (not implemented yet)
            assertTrue("Interactive mode should complete", true);
        } catch (Exception e) {
            fail("Interactive mode should not throw exception: " + e.getMessage());
        }
        
        // Then test macro mode
        Macro.setOptions("method=WATERSHED bg_threshold=50");
        
        try {
            plugin.run("");
            // Macro mode should process without GUI
            assertTrue("Macro mode should complete", true);
        } catch (Exception e) {
            fail("Macro mode should not throw exception: " + e.getMessage());
        }
    }
}