package jp.yourorg.fiji_maxima_based_segmenter.core;

import org.junit.Test;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.*;

/**
 * Unit tests for LabelKey class.
 * Validates Requirements 4.3: LabelKey must properly represent (sliceIndex, label) combinations
 * with correct equals() and hashCode() implementation.
 */
public class LabelKeyTest {
    
    @Test
    public void testConstructor() {
        LabelKey key = new LabelKey(5, 10);
        assertEquals(5, key.sliceIndex);
        assertEquals(10, key.label);
    }
    
    @Test
    public void testEqualsWithSameValues() {
        LabelKey key1 = new LabelKey(3, 7);
        LabelKey key2 = new LabelKey(3, 7);
        assertEquals(key1, key2);
    }
    
    @Test
    public void testEqualsWithDifferentSliceIndex() {
        LabelKey key1 = new LabelKey(3, 7);
        LabelKey key2 = new LabelKey(4, 7);
        assertNotEquals(key1, key2);
    }
    
    @Test
    public void testEqualsWithDifferentLabel() {
        LabelKey key1 = new LabelKey(3, 7);
        LabelKey key2 = new LabelKey(3, 8);
        assertNotEquals(key1, key2);
    }
    
    @Test
    public void testEqualsWithNull() {
        LabelKey key = new LabelKey(3, 7);
        assertNotEquals(key, null);
    }
    
    @Test
    public void testEqualsWithDifferentClass() {
        LabelKey key = new LabelKey(3, 7);
        String other = "not a LabelKey";
        assertNotEquals(key, other);
    }
    
    @Test
    public void testEqualsSameInstance() {
        LabelKey key = new LabelKey(3, 7);
        assertEquals(key, key);
    }
    
    @Test
    public void testHashCodeConsistency() {
        LabelKey key1 = new LabelKey(3, 7);
        LabelKey key2 = new LabelKey(3, 7);
        assertEquals(key1.hashCode(), key2.hashCode());
    }
    
    @Test
    public void testHashCodeDifferentForDifferentKeys() {
        LabelKey key1 = new LabelKey(3, 7);
        LabelKey key2 = new LabelKey(3, 8);
        LabelKey key3 = new LabelKey(4, 7);
        
        // While hash codes can collide, these specific values should be different
        assertNotEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1.hashCode(), key3.hashCode());
    }
    
    @Test
    public void testHashMapUsage() {
        HashMap<LabelKey, String> map = new HashMap<>();
        LabelKey key1 = new LabelKey(1, 5);
        LabelKey key2 = new LabelKey(2, 10);
        LabelKey key3 = new LabelKey(1, 5); // Same as key1
        
        map.put(key1, "value1");
        map.put(key2, "value2");
        
        assertEquals("value1", map.get(key1));
        assertEquals("value2", map.get(key2));
        assertEquals("value1", map.get(key3)); // Should retrieve same value as key1
        assertEquals(2, map.size()); // Should only have 2 entries
    }
    
    @Test
    public void testHashSetUsage() {
        HashSet<LabelKey> set = new HashSet<>();
        LabelKey key1 = new LabelKey(1, 5);
        LabelKey key2 = new LabelKey(2, 10);
        LabelKey key3 = new LabelKey(1, 5); // Same as key1
        
        set.add(key1);
        set.add(key2);
        set.add(key3);
        
        assertEquals(2, set.size()); // Should only have 2 unique keys
        assertTrue(set.contains(key1));
        assertTrue(set.contains(key2));
        assertTrue(set.contains(key3));
    }
    
    @Test
    public void testToString() {
        LabelKey key = new LabelKey(3, 7);
        String str = key.toString();
        assertTrue(str.contains("3"));
        assertTrue(str.contains("7"));
        assertTrue(str.contains("LabelKey"));
    }
    
    @Test
    public void testZeroBasedSliceIndex() {
        LabelKey key = new LabelKey(0, 1);
        assertEquals(0, key.sliceIndex);
    }
    
    @Test
    public void testBackgroundLabel() {
        LabelKey key = new LabelKey(0, 0);
        assertEquals(0, key.label);
    }
    
    @Test
    public void testLargeValues() {
        LabelKey key = new LabelKey(1000, 5000);
        assertEquals(1000, key.sliceIndex);
        assertEquals(5000, key.label);
    }
    
    @Test
    public void testNegativeValues() {
        // While not typical, the class should handle negative values
        LabelKey key = new LabelKey(-1, -1);
        assertEquals(-1, key.sliceIndex);
        assertEquals(-1, key.label);
    }
}
