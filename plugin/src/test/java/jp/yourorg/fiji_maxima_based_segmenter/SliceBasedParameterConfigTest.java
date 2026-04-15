package jp.yourorg.fiji_maxima_based_segmenter;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test class for parameter configuration functionality in Slice_Based_3D_Segmenter_.
 *
 * This test class verifies that the simplified segment API correctly handles
 * its bgThreshold and tolerance parameters.
 */
public class SliceBasedParameterConfigTest {

    private ImagePlus testImage;

    @Before
    public void setUp() {
        // Create a simple 3D test image (3 slices, 10x10 pixels)
        testImage = createTestImage();
    }

    private ImagePlus createTestImage() {
        ImageStack stack = new ImageStack(10, 10);
        for (int i = 0; i < 3; i++) {
            ByteProcessor bp = new ByteProcessor(10, 10);
            // Fill with some test data
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    bp.set(x, y, (i + 1) * 50 + x + y);
                }
            }
            stack.addSlice("slice" + (i + 1), bp);
        }
        return new ImagePlus("test", stack);
    }

    @Test
    public void testSegmentAPI_ParameterIntegration() {
        // Test that the segment API properly integrates parameter configuration
        // This tests the full parameter flow with actual segmentation
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(testImage, 25, 200.0);

        // Since segmentation logic is now implemented, result should not be null
        // and should be a valid 3D label image
        assertNotNull("Result should not be null since segmentation logic is now implemented", result);
        assertEquals("Result should have same dimensions as input", testImage.getWidth(), result.getWidth());
        assertEquals("Result should have same dimensions as input", testImage.getHeight(), result.getHeight());
        assertEquals("Result should have same dimensions as input", testImage.getNSlices(), result.getNSlices());
    }

    @Test
    public void testSegmentAPI_ZeroThreshold() {
        // Test segmentation with zero background threshold
        ImagePlus result = Slice_Based_3D_Segmenter_.segment(testImage, 0, 10.0);

        // Should still produce a valid result
        assertNotNull("Result should not be null with zero bg threshold", result);
    }

    @Test
    public void testSegmentAPI_HighThreshold() {
        // Test segmentation with a very high background threshold
        // This should result in few or no detected regions
        try {
            ImagePlus result = Slice_Based_3D_Segmenter_.segment(testImage, 250, 10.0);
            // Result may be null if no regions are detected, which is valid behavior
        } catch (Exception e) {
            fail("Segment API should not throw exceptions for high threshold: " + e.getMessage());
        }
    }

    @Test
    public void testSegmentAPI_ZeroTolerance() {
        // Test segmentation with zero tolerance
        try {
            ImagePlus result = Slice_Based_3D_Segmenter_.segment(testImage, 25, 0.0);
            // Should complete without exceptions
        } catch (Exception e) {
            fail("Segment API should not throw exceptions for zero tolerance: " + e.getMessage());
        }
    }
}
