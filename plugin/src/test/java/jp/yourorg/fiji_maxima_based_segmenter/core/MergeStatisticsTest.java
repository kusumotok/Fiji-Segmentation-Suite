package jp.yourorg.fiji_maxima_based_segmenter.core;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for MergeStatistics class.
 * Validates Requirements 13.1, 13.2, 13.3: MergeStatistics must hold merge processing statistics
 * and provide a human-readable summary via toSummaryString().
 */
public class MergeStatisticsTest {
    
    @Test
    public void testConstructor() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        assertEquals(10, stats.totalSlices);
        assertEquals(50, stats.total2DRegions);
        assertEquals(30, stats.total3DRegions);
        assertEquals(1500, stats.processingTimeMs);
    }
    
    @Test
    public void testConstructorWithZeroValues() {
        MergeStatistics stats = new MergeStatistics(0, 0, 0, 0);
        assertEquals(0, stats.totalSlices);
        assertEquals(0, stats.total2DRegions);
        assertEquals(0, stats.total3DRegions);
        assertEquals(0, stats.processingTimeMs);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNegativeTotalSlices() {
        new MergeStatistics(-1, 50, 30, 1500);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNegativeTotal2DRegions() {
        new MergeStatistics(10, -1, 30, 1500);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNegativeTotal3DRegions() {
        new MergeStatistics(10, 50, -1, 1500);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNegativeProcessingTime() {
        new MergeStatistics(10, 50, 30, -1);
    }
    
    @Test
    public void testToSummaryStringFormat() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        String summary = stats.toSummaryString();
        
        // Check that summary contains expected information
        assertTrue(summary.contains("Slice-Based 3D Segmentation Summary"));
        assertTrue(summary.contains("Slices processed: 10"));
        assertTrue(summary.contains("2D regions detected: 50"));
        assertTrue(summary.contains("3D regions after merging: 30"));
        assertTrue(summary.contains("Processing time: 1.50 seconds"));
        assertTrue(summary.contains("Merge ratio: 0.60"));
    }
    
    @Test
    public void testToSummaryStringWithZero2DRegions() {
        MergeStatistics stats = new MergeStatistics(10, 0, 0, 1000);
        String summary = stats.toSummaryString();
        
        // Should not contain merge ratio when there are no 2D regions
        assertFalse(summary.contains("Merge ratio"));
        assertTrue(summary.contains("2D regions detected: 0"));
        assertTrue(summary.contains("3D regions after merging: 0"));
    }
    
    @Test
    public void testToSummaryStringWithLargeProcessingTime() {
        MergeStatistics stats = new MergeStatistics(100, 500, 300, 65000);
        String summary = stats.toSummaryString();
        
        // 65000ms = 65.00 seconds
        assertTrue(summary.contains("Processing time: 65.00 seconds"));
    }
    
    @Test
    public void testToSummaryStringWithSmallProcessingTime() {
        MergeStatistics stats = new MergeStatistics(5, 20, 15, 123);
        String summary = stats.toSummaryString();
        
        // 123ms = 0.12 seconds
        assertTrue(summary.contains("Processing time: 0.12 seconds"));
    }
    
    @Test
    public void testToSummaryStringMergeRatioCalculation() {
        // Test case where 3D regions = 2D regions (no merging occurred)
        MergeStatistics stats1 = new MergeStatistics(10, 50, 50, 1000);
        String summary1 = stats1.toSummaryString();
        assertTrue(summary1.contains("Merge ratio: 1.00"));
        
        // Test case where 3D regions < 2D regions (merging occurred)
        MergeStatistics stats2 = new MergeStatistics(10, 100, 25, 1000);
        String summary2 = stats2.toSummaryString();
        assertTrue(summary2.contains("Merge ratio: 0.25"));
    }
    
    @Test
    public void testToString() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        String str = stats.toString();
        
        assertTrue(str.contains("MergeStatistics"));
        assertTrue(str.contains("slices=10"));
        assertTrue(str.contains("2D=50"));
        assertTrue(str.contains("3D=30"));
        assertTrue(str.contains("time=1500ms"));
    }
    
    @Test
    public void testToStringWithZeroValues() {
        MergeStatistics stats = new MergeStatistics(0, 0, 0, 0);
        String str = stats.toString();
        
        assertTrue(str.contains("slices=0"));
        assertTrue(str.contains("2D=0"));
        assertTrue(str.contains("3D=0"));
        assertTrue(str.contains("time=0ms"));
    }
    
    @Test
    public void testImmutability() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);

        // Verify that fields are final and cannot be changed
        // (This is enforced at compile time, but we can verify values don't change)
        assertEquals(10, stats.totalSlices);
        assertEquals(50, stats.total2DRegions);
        assertEquals(30, stats.total3DRegions);
        assertEquals(1500, stats.processingTimeMs);
        assertTrue(stats.voxelCounts.isEmpty());
    }
    
    @Test
    public void testRealisticScenario() {
        // Simulate a realistic 3D segmentation scenario
        // 50 slices, 200 2D regions total, merged into 80 3D regions, took 5 seconds
        MergeStatistics stats = new MergeStatistics(50, 200, 80, 5000);
        String summary = stats.toSummaryString();
        
        assertTrue(summary.contains("Slices processed: 50"));
        assertTrue(summary.contains("2D regions detected: 200"));
        assertTrue(summary.contains("3D regions after merging: 80"));
        assertTrue(summary.contains("Processing time: 5.00 seconds"));
        assertTrue(summary.contains("Merge ratio: 0.40"));
    }
    
    @Test
    public void testNoMergingScenario() {
        // Scenario where no merging occurred (each 2D region is a separate 3D region)
        MergeStatistics stats = new MergeStatistics(10, 30, 30, 2000);
        String summary = stats.toSummaryString();
        
        assertTrue(summary.contains("Merge ratio: 1.00"));
    }
    
    @Test
    public void testHighMergingScenario() {
        // Scenario where significant merging occurred
        // 100 2D regions merged into just 10 3D regions
        MergeStatistics stats = new MergeStatistics(20, 100, 10, 3000);
        String summary = stats.toSummaryString();
        
        assertTrue(summary.contains("Merge ratio: 0.10"));
    }
    
    @Test
    public void testSummaryStringContainsBorders() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        String summary = stats.toSummaryString();
        
        // Check for header and footer borders
        assertTrue(summary.contains("==="));
        assertTrue(summary.contains("=========================================="));
    }
    
    @Test
    public void testSummaryStringMultipleLines() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        String summary = stats.toSummaryString();

        // Summary should contain multiple lines
        String[] lines = summary.split("\n");
        assertTrue(lines.length >= 6); // At least 6 lines expected
    }

    // --- Voxel count tests (Requirement 13.2) ---

    @Test
    public void testConstructorWithVoxelCounts() {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        voxelCounts.put(1, 100);
        voxelCounts.put(2, 250);
        voxelCounts.put(3, 50);

        MergeStatistics stats = new MergeStatistics(10, 50, 3, 1500, voxelCounts);
        assertEquals(3, stats.voxelCounts.size());
        assertEquals(Integer.valueOf(100), stats.voxelCounts.get(1));
        assertEquals(Integer.valueOf(250), stats.voxelCounts.get(2));
        assertEquals(Integer.valueOf(50), stats.voxelCounts.get(3));
    }

    @Test
    public void testVoxelCountsAreUnmodifiable() {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        voxelCounts.put(1, 100);

        MergeStatistics stats = new MergeStatistics(10, 50, 1, 1500, voxelCounts);

        try {
            stats.voxelCounts.put(2, 200);
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testVoxelCountsDefensiveCopy() {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        voxelCounts.put(1, 100);

        MergeStatistics stats = new MergeStatistics(10, 50, 1, 1500, voxelCounts);

        // Modifying original map should not affect stats
        voxelCounts.put(2, 200);
        assertEquals(1, stats.voxelCounts.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullVoxelCounts() {
        new MergeStatistics(10, 50, 30, 1500, null);
    }

    @Test
    public void testToSummaryStringWithVoxelCounts() {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        voxelCounts.put(1, 100);
        voxelCounts.put(2, 250);
        voxelCounts.put(3, 50);

        MergeStatistics stats = new MergeStatistics(10, 50, 3, 1500, voxelCounts);
        String summary = stats.toSummaryString();

        // Check voxel count section
        assertTrue(summary.contains("Voxel Counts per 3D Region"));
        assertTrue(summary.contains("Region 1: 100 voxels"));
        assertTrue(summary.contains("Region 2: 250 voxels"));
        assertTrue(summary.contains("Region 3: 50 voxels"));
        assertTrue(summary.contains("Total foreground voxels: 400"));
        assertTrue(summary.contains("Average region size: 133.3 voxels"));
        assertTrue(summary.contains("Size range: 50 - 250 voxels"));
    }

    @Test
    public void testToSummaryStringWithoutVoxelCounts() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        String summary = stats.toSummaryString();

        // Without voxel counts, the voxel section should not appear
        assertFalse(summary.contains("Voxel Counts per 3D Region"));
        assertFalse(summary.contains("Total foreground voxels"));
    }

    @Test
    public void testDefaultConstructorHasEmptyVoxelCounts() {
        MergeStatistics stats = new MergeStatistics(10, 50, 30, 1500);
        assertNotNull(stats.voxelCounts);
        assertTrue(stats.voxelCounts.isEmpty());
    }

    @Test
    public void testVoxelCountsSingleRegion() {
        Map<Integer, Integer> voxelCounts = new HashMap<>();
        voxelCounts.put(1, 500);

        MergeStatistics stats = new MergeStatistics(5, 10, 1, 1000, voxelCounts);
        String summary = stats.toSummaryString();

        assertTrue(summary.contains("Region 1: 500 voxels"));
        assertTrue(summary.contains("Total foreground voxels: 500"));
        assertTrue(summary.contains("Average region size: 500.0 voxels"));
        assertTrue(summary.contains("Size range: 500 - 500 voxels"));
    }
}
