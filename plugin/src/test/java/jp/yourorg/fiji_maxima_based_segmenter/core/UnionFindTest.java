package jp.yourorg.fiji_maxima_based_segmenter.core;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.Assert.*;

/**
 * Unit tests for UnionFind data structure.
 * 
 * Tests basic union/find operations, group formation, and edge cases.
 */
public class UnionFindTest {
    
    @Test
    public void testConstructorInitialization() {
        UnionFind uf = new UnionFind(5);
        
        // Each element should be its own representative
        for (int i = 0; i < 5; i++) {
            assertEquals("Element " + i + " should be its own parent initially", i, uf.find(i));
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidSizeZero() {
        new UnionFind(0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructorInvalidSizeNegative() {
        new UnionFind(-1);
    }
    
    @Test
    public void testFindWithoutUnion() {
        UnionFind uf = new UnionFind(3);
        
        assertEquals(0, uf.find(0));
        assertEquals(1, uf.find(1));
        assertEquals(2, uf.find(2));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindOutOfBoundsNegative() {
        UnionFind uf = new UnionFind(5);
        uf.find(-1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindOutOfBoundsEqual() {
        UnionFind uf = new UnionFind(5);
        uf.find(5);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testFindOutOfBoundsLarge() {
        UnionFind uf = new UnionFind(5);
        uf.find(10);
    }
    
    @Test
    public void testBasicUnion() {
        UnionFind uf = new UnionFind(5);
        
        uf.union(0, 1);
        
        // 0 and 1 should now have the same representative
        assertEquals(uf.find(0), uf.find(1));
        
        // Other elements should still be separate
        assertNotEquals(uf.find(0), uf.find(2));
        assertNotEquals(uf.find(0), uf.find(3));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testUnionOutOfBoundsFirst() {
        UnionFind uf = new UnionFind(5);
        uf.union(-1, 0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testUnionOutOfBoundsSecond() {
        UnionFind uf = new UnionFind(5);
        uf.union(0, 5);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testUnionOutOfBoundsBoth() {
        UnionFind uf = new UnionFind(5);
        uf.union(10, 20);
    }
    
    @Test
    public void testMultipleUnions() {
        UnionFind uf = new UnionFind(5);
        
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(2, 3);
        
        // All should have the same representative
        int root = uf.find(0);
        assertEquals(root, uf.find(1));
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        
        // Element 4 should still be separate
        assertNotEquals(root, uf.find(4));
    }
    
    @Test
    public void testUnionAlreadyConnected() {
        UnionFind uf = new UnionFind(3);
        
        uf.union(0, 1);
        int rootBefore = uf.find(0);
        
        // Union again - should have no effect
        uf.union(0, 1);
        uf.union(1, 0);
        
        int rootAfter = uf.find(0);
        assertEquals(rootBefore, rootAfter);
        assertEquals(uf.find(0), uf.find(1));
    }
    
    @Test
    public void testGetGroupsNoUnions() {
        UnionFind uf = new UnionFind(3);
        
        Map<Integer, List<Integer>> groups = uf.getGroups();
        
        // Should have 3 groups, each with one element
        assertEquals(3, groups.size());
        
        for (int i = 0; i < 3; i++) {
            assertTrue(groups.containsKey(i));
            assertEquals(1, groups.get(i).size());
            assertTrue(groups.get(i).contains(i));
        }
    }
    
    @Test
    public void testGetGroupsWithUnions() {
        UnionFind uf = new UnionFind(6);
        
        // Create two groups: {0, 1, 2} and {3, 4}
        // Element 5 stays alone
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(3, 4);
        
        Map<Integer, List<Integer>> groups = uf.getGroups();
        
        // Should have 3 groups
        assertEquals(3, groups.size());
        
        // Verify group sizes
        Set<Integer> groupSizes = new HashSet<>();
        for (List<Integer> group : groups.values()) {
            groupSizes.add(group.size());
        }
        assertTrue(groupSizes.contains(3)); // Group with 0, 1, 2
        assertTrue(groupSizes.contains(2)); // Group with 3, 4
        assertTrue(groupSizes.contains(1)); // Group with 5
        
        // Verify elements in same group have same representative
        assertEquals(uf.find(0), uf.find(1));
        assertEquals(uf.find(1), uf.find(2));
        assertEquals(uf.find(3), uf.find(4));
        
        // Verify elements in different groups have different representatives
        assertNotEquals(uf.find(0), uf.find(3));
        assertNotEquals(uf.find(0), uf.find(5));
        assertNotEquals(uf.find(3), uf.find(5));
    }
    
    @Test
    public void testGetGroupsAllConnected() {
        UnionFind uf = new UnionFind(4);
        
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(2, 3);
        
        Map<Integer, List<Integer>> groups = uf.getGroups();
        
        // Should have exactly 1 group with all 4 elements
        assertEquals(1, groups.size());
        
        List<Integer> group = groups.values().iterator().next();
        assertEquals(4, group.size());
        
        // All elements should be in the group
        assertTrue(group.contains(0));
        assertTrue(group.contains(1));
        assertTrue(group.contains(2));
        assertTrue(group.contains(3));
    }
    
    @Test
    public void testPathCompression() {
        UnionFind uf = new UnionFind(5);
        
        // Create a chain: 0 -> 1 -> 2 -> 3 -> 4
        uf.union(0, 1);
        uf.union(1, 2);
        uf.union(2, 3);
        uf.union(3, 4);
        
        // Find on element 0 should trigger path compression
        int root = uf.find(0);
        
        // All elements should now point directly to the root
        assertEquals(root, uf.find(0));
        assertEquals(root, uf.find(1));
        assertEquals(root, uf.find(2));
        assertEquals(root, uf.find(3));
        assertEquals(root, uf.find(4));
    }
    
    @Test
    public void testGetSize() {
        UnionFind uf1 = new UnionFind(5);
        assertEquals(5, uf1.getSize());
        
        UnionFind uf2 = new UnionFind(100);
        assertEquals(100, uf2.getSize());
        
        // Size should not change after unions
        uf1.union(0, 1);
        uf1.union(2, 3);
        assertEquals(5, uf1.getSize());
    }
    
    @Test
    public void testComplexScenario() {
        UnionFind uf = new UnionFind(10);
        
        // Create groups: {0,1,2}, {3,4,5,6}, {7,8}, {9}
        uf.union(0, 1);
        uf.union(1, 2);
        
        uf.union(3, 4);
        uf.union(4, 5);
        uf.union(5, 6);
        
        uf.union(7, 8);
        
        Map<Integer, List<Integer>> groups = uf.getGroups();
        
        // Should have 4 groups
        assertEquals(4, groups.size());
        
        // Verify group sizes
        Set<Integer> groupSizes = new HashSet<>();
        for (List<Integer> group : groups.values()) {
            groupSizes.add(group.size());
        }
        assertTrue(groupSizes.contains(3)); // {0,1,2}
        assertTrue(groupSizes.contains(4)); // {3,4,5,6}
        assertTrue(groupSizes.contains(2)); // {7,8}
        assertTrue(groupSizes.contains(1)); // {9}
        
        // Verify connectivity within groups
        assertEquals(uf.find(0), uf.find(2));
        assertEquals(uf.find(3), uf.find(6));
        assertEquals(uf.find(7), uf.find(8));
        
        // Verify separation between groups
        assertNotEquals(uf.find(0), uf.find(3));
        assertNotEquals(uf.find(0), uf.find(7));
        assertNotEquals(uf.find(3), uf.find(7));
        assertNotEquals(uf.find(0), uf.find(9));
    }
}
