package jp.yourorg.fiji_maxima_based_segmenter;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Integration tests for macro mode functionality in Slice_Based_3D_Segmenter_.
 * Tests the complete macro workflow including parameter parsing and segmentation.
 */
public class SliceBasedMacroModeTest {

    private ImagePlus testImage;

    @Before
    public void setUp() {
        testImage = createTestImage();
    }

    private ImagePlus createTestImage() {
        // Create a simple 3D test image with distinct regions in each slice
        int width = 20, height = 20, slices = 3;
        ImagePlus imp = IJ.createImage("Test Stack", "8-bit", width, height, slices);

        // Slice 1: Circle in center with high intensity
        imp.setSlice(1);
        ImageProcessor ip1 = imp.getProcessor();
        ip1.setColor(200);  // High intensity for reliable detection
        ip1.fillOval(5, 5, 10, 10);

        // Slice 2: Rectangle overlapping with circle
        imp.setSlice(2);
        ImageProcessor ip2 = imp.getProcessor();
        ip2.setColor(200);  // High intensity for reliable detection
        ip2.fillRect(7, 7, 8, 8);

        // Slice 3: Small circle overlapping with rectangle
        imp.setSlice(3);
        ImageProcessor ip3 = imp.getProcessor();
        ip3.setColor(200);  // High intensity for reliable detection
        ip3.fillOval(8, 8, 6, 6);

        return imp;
    }

    @Test
    public void testSegmentAPI_WithMacroParameters() throws Exception {
        // Test the static segment API with simplified parameters
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            testImage,
            10,    // bg_threshold
            50.0   // tolerance
        );

        // Verify that segmentation produces a result
        assertNotNull("Segmentation should produce a result", result);
        assertEquals("Result should have same dimensions as input", testImage.getWidth(), result.getWidth());
        assertEquals("Result should have same dimensions as input", testImage.getHeight(), result.getHeight());
        assertEquals("Result should have same dimensions as input", testImage.getNSlices(), result.getNSlices());

        // Clean up
        result.close();
    }

    @Test
    public void testSegmentAPI_NullImage() {
        // Test that null image is handled gracefully
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            null,
            10,    // bg_threshold
            50.0   // tolerance
        );

        assertNull("Segmentation with null input should return null", result);
    }

    @Test
    public void testSegmentAPI_SingleSlice() {
        // Test that single slice image is rejected
        ImagePlus singleSlice = IJ.createImage("Single", "8-bit", 20, 20, 1);

        ImagePlus result = Slice_Based_3D_Segmenter_.segment(
            singleSlice,
            10,    // bg_threshold
            50.0   // tolerance
        );

        assertNull("Segmentation with single slice should return null", result);
        singleSlice.close();
    }
}
