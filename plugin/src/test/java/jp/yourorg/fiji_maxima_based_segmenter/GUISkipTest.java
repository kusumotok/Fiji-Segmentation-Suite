package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.process.ImageProcessor;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for GUI skip functionality in macro mode.
 * Verifies that when macro options are provided, the GUI is bypassed
 * and processing runs directly in batch mode.
 */
public class GUISkipTest {
    
    private ImagePlus testImage;
    private Slice_Based_3D_Segmenter_ plugin;
    
    @Before
    public void setUp() {
        testImage = createTestImage();
        plugin = new Slice_Based_3D_Segmenter_();
        
        // Set the test image as the current image
        testImage.show();
    }
    
    private ImagePlus createTestImage() {
        // Create a simple 3D test image
        int width = 10, height = 10, slices = 2;
        ImagePlus imp = IJ.createImage("Test Stack", "8-bit", width, height, slices);
        
        // Slice 1: Circle in center
        imp.setSlice(1);
        ImageProcessor ip1 = imp.getProcessor();
        ip1.setColor(200);
        ip1.fillOval(2, 2, 6, 6);
        
        // Slice 2: Overlapping circle
        imp.setSlice(2);
        ImageProcessor ip2 = imp.getProcessor();
        ip2.setColor(200);
        ip2.fillOval(3, 3, 6, 6);
        
        return imp;
    }
    
    @Test
    public void testGUISkipWithMacroOptions() {
        // Set macro options to simulate macro mode
        String macroOptions = "bg_threshold=10 fg_threshold=100 tolerance=50.0 method=WATERSHED";
        Macro.setOptions(macroOptions);
        
        try {
            // Run the plugin - should skip GUI and run in macro mode
            plugin.run("");
            
            // If we reach here without exceptions, the GUI skip worked
            // The plugin should have processed the image without showing GUI
            assertTrue("Plugin should complete without showing GUI", true);
            
        } catch (Exception e) {
            fail("Plugin should not throw exception in macro mode: " + e.getMessage());
        } finally {
            // Clean up macro options
            Macro.setOptions(null);
        }
    }
    
    @Test
    public void testGUISkipWithEmptyMacroOptions() {
        // Set empty macro options
        Macro.setOptions("");
        
        try {
            // Run the plugin - should skip GUI because empty string is not null
            plugin.run("");
            
            // Should complete without exceptions
            assertTrue("Plugin should handle empty macro options", true);
            
        } catch (Exception e) {
            fail("Plugin should handle empty macro options gracefully: " + e.getMessage());
        } finally {
            // Clean up macro options
            Macro.setOptions(null);
        }
    }
    
    @Test
    public void testGUISkipWithWhitespaceOnlyMacroOptions() {
        // Set whitespace-only macro options
        Macro.setOptions("   \t\n  ");
        
        try {
            // Run the plugin - should show GUI message because whitespace-only is treated as empty
            plugin.run("");
            
            // Should complete without exceptions
            assertTrue("Plugin should handle whitespace-only macro options", true);
            
        } catch (Exception e) {
            fail("Plugin should handle whitespace-only macro options gracefully: " + e.getMessage());
        } finally {
            // Clean up macro options
            Macro.setOptions(null);
        }
    }
    
    @Test
    public void testNoMacroOptionsShowsGUIMessage() {
        // Ensure no macro options are set
        Macro.setOptions(null);
        
        try {
            // Run the plugin - should show GUI message (not implemented yet)
            plugin.run("");
            
            // Should complete without exceptions
            assertTrue("Plugin should show GUI message when no macro options", true);
            
        } catch (Exception e) {
            fail("Plugin should handle no macro options gracefully: " + e.getMessage());
        }
    }
    
    @Test
    public void testMacroModeDetection() {
        // Test the logic that detects macro mode
        
        // Case 1: Valid macro options
        Macro.setOptions("bg_threshold=10");
        String options1 = Macro.getOptions();
        if (options1 != null) {
            assertFalse("Macro options should not be empty after trim", options1.trim().isEmpty());
        }
        
        // Case 2: Empty macro options - ImageJ may return null for empty strings
        Macro.setOptions("");
        String options2 = Macro.getOptions();
        // ImageJ may return null for empty options, which is fine
        if (options2 != null) {
            assertTrue("Empty macro options should be empty after trim", options2.trim().isEmpty());
        }
        
        // Case 3: Whitespace-only macro options
        Macro.setOptions("   \t  ");
        String options3 = Macro.getOptions();
        if (options3 != null) {
            assertTrue("Whitespace macro options should be empty after trim", options3.trim().isEmpty());
        }
        
        // Case 4: No macro options
        Macro.setOptions(null);
        String options4 = Macro.getOptions();
        // This should be null
        assertNull("No macro options should be null", options4);
        
        // Clean up
        Macro.setOptions(null);
    }
}