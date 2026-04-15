package jp.yourorg.fiji_maxima_based_segmenter.core;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for SliceSegmentationResult data class.
 * Tests construction, validation, and basic functionality.
 * Validates Requirements 3.4: SliceSegmentationResult must hold slice segmentation results.
 */
public class SliceSegmentationResultTest {
    
    @Test
    public void testConstructorWithValidParameters() {
        // Create a simple label image
        ByteProcessor bp = new ByteProcessor(10, 10);
        ImagePlus labelImage = new ImagePlus("test", bp);
        
        SliceSegmentationResult result = new SliceSegmentationResult(0, labelImage, 5);
        
        assertEquals(0, result.sliceIndex);
        assertSame(labelImage, result.labelImage);
        assertEquals(5, result.regionCount);
    }
    
    @Test
    public void testConstructorWithZeroRegions() {
        // Test with zero regions (empty slice)
        ByteProcessor bp = new ByteProcessor(10, 10);
        ImagePlus labelImage = new ImagePlus("test", bp);
        
        SliceSegmentationResult result = new SliceSegmentationResult(3, labelImage, 0);
        
        assertEquals(3, result.sliceIndex);
        assertEquals(0, result.regionCount);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorThrowsOnNullLabelImage() {
        new SliceSegmentationResult(0, null, 5);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorThrowsOnNegativeRegionCount() {
        ByteProcessor bp = new ByteProcessor(10, 10);
        ImagePlus labelImage = new ImagePlus("test", bp);
        
        new SliceSegmentationResult(0, labelImage, -1);
    }
    
    @Test
    public void testToString() {
        ByteProcessor bp = new ByteProcessor(10, 10);
        ImagePlus labelImage = new ImagePlus("test", bp);
        
        SliceSegmentationResult result = new SliceSegmentationResult(2, labelImage, 7);
        
        String str = result.toString();
        assertTrue(str.contains("slice=2"));
        assertTrue(str.contains("regions=7"));
    }
    
    @Test
    public void testImmutability() {
        // Verify that fields are final and cannot be changed
        ByteProcessor bp = new ByteProcessor(10, 10);
        ImagePlus labelImage = new ImagePlus("test", bp);
        
        SliceSegmentationResult result = new SliceSegmentationResult(1, labelImage, 3);
        
        // Fields should be accessible but immutable
        assertEquals(1, result.sliceIndex);
        assertEquals(3, result.regionCount);
        assertNotNull(result.labelImage);
    }
}
