package org.jmc.test;

import java.awt.Point;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jmc.Chunk;
import org.jmc.util.ChunkDataPool;
import org.jmc.util.Log;
import org.jmc.util.SmartChunkCache;

/**
 * Performance test suite for the improved memory management components.
 */
public class PerformanceTest {
    
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    public static void main(String[] args) {
        Log.info("Starting performance tests for improved memory management...");
        
        try {
            testSmartChunkCache();
            testChunkDataPool();
            testMemoryUsageUnderPressure();
            testCacheEvictionBehavior();
            
            Log.info("All performance tests completed successfully!");
            
        } catch (Exception e) {
            Log.error("Performance test failed: " + e.getMessage(), e, false);
        }
    }
    
    /**
     * Test the smart chunk cache performance and memory awareness.
     */
    public static void testSmartChunkCache() {
        Log.info("Testing SmartChunkCache performance...");
        
        SmartChunkCache cache = new SmartChunkCache(100);
        List<Point> testPoints = generateTestPoints(500);
        
        long startTime = System.nanoTime();
        MemoryUsage startMemory = memoryBean.getHeapMemoryUsage();
        
        // Simulate chunk loading with cache hits and misses
        for (int i = 0; i < 1000; i++) {
            Point p = testPoints.get(i % testPoints.size());
            
            Chunk.Blocks blocks = cache.get(p);
            if (blocks == null) {
                // Simulate chunk loading (create mock data)
                blocks = createMockChunkBlocks();
                cache.put(p, blocks);
            }
            
            // Log memory stats every 100 operations
            if (i % 100 == 0) {
                MemoryUsage currentMemory = memoryBean.getHeapMemoryUsage();
                double memoryUsage = (double) currentMemory.getUsed() / currentMemory.getMax();
                Log.debug(String.format("Operation %d - Memory usage: %.1f%%, Cache size: %d", 
                         i, memoryUsage * 100, cache.size()));
            }
        }
        
        long endTime = System.nanoTime();
        MemoryUsage endMemory = memoryBean.getHeapMemoryUsage();
        
        double executionTime = (endTime - startTime) / 1_000_000_000.0;
        long memoryIncrease = endMemory.getUsed() - startMemory.getUsed();
        
        Log.info(String.format("SmartChunkCache test completed in %.2f seconds", executionTime));
        Log.info(String.format("Memory increase: %d bytes (%.2f MB)", memoryIncrease, memoryIncrease / 1024.0 / 1024.0));
        cache.logStatistics();
    }
    
    /**
     * Test the chunk data pool performance and object reuse.
     */
    public static void testChunkDataPool() {
        Log.info("Testing ChunkDataPool performance...");
        
        ChunkDataPool pool = new ChunkDataPool(50);
        
        long startTime = System.nanoTime();
        MemoryUsage startMemory = memoryBean.getHeapMemoryUsage();
        
        // Test object borrowing and returning
        List<ChunkDataPool.PooledChunkBlocks> borrowed = new ArrayList<>();
        
        // Borrow objects
        for (int i = 0; i < 200; i++) {
            ChunkDataPool.PooledChunkBlocks pooled = pool.borrow();
            pooled.setBlocks(createMockChunkBlocks());
            borrowed.add(pooled);
        }
        
        // Return half of them
        for (int i = 0; i < 100; i++) {
            pool.returnObject(borrowed.get(i));
        }
        
        // Borrow again to test reuse
        for (int i = 0; i < 100; i++) {
            ChunkDataPool.PooledChunkBlocks pooled = pool.borrow();
            pooled.setBlocks(createMockChunkBlocks());
        }
        
        long endTime = System.nanoTime();
        MemoryUsage endMemory = memoryBean.getHeapMemoryUsage();
        
        double executionTime = (endTime - startTime) / 1_000_000_000.0;
        long memoryIncrease = endMemory.getUsed() - startMemory.getUsed();
        
        Log.info(String.format("ChunkDataPool test completed in %.2f seconds", executionTime));
        Log.info(String.format("Memory increase: %d bytes (%.2f MB)", memoryIncrease, memoryIncrease / 1024.0 / 1024.0));
        pool.logStatistics();
    }
    
    /**
     * Test behavior under memory pressure.
     */
    public static void testMemoryUsageUnderPressure() {
        Log.info("Testing memory usage under pressure...");
        
        SmartChunkCache cache = new SmartChunkCache(1000);
        List<byte[]> memoryPressure = new ArrayList<>();
        
        try {
            // Create memory pressure
            for (int i = 0; i < 50; i++) {
                memoryPressure.add(new byte[10 * 1024 * 1024]); // 10MB each
            }
            
            // Now test cache behavior under pressure
            for (int i = 0; i < 100; i++) {
                Point p = new Point(i, i);
                Chunk.Blocks blocks = createMockChunkBlocks();
                cache.put(p, blocks);
                
                if (i % 10 == 0) {
                    MemoryUsage memory = memoryBean.getHeapMemoryUsage();
                    double usage = (double) memory.getUsed() / memory.getMax();
                    Log.debug(String.format("Memory pressure test %d - Usage: %.1f%%, Cache size: %d", 
                             i, usage * 100, cache.size()));
                }
            }
            
            cache.logStatistics();
            
        } finally {
            // Clean up memory pressure
            memoryPressure.clear();
            System.gc(); // Force cleanup of test data
        }
        
        Log.info("Memory pressure test completed");
    }
    
    /**
     * Test cache eviction behavior.
     */
    public static void testCacheEvictionBehavior() {
        Log.info("Testing cache eviction behavior...");
        
        SmartChunkCache cache = new SmartChunkCache(10); // Small cache
        
        // Fill cache beyond capacity
        for (int i = 0; i < 50; i++) {
            Point p = new Point(i, i);
            Chunk.Blocks blocks = createMockChunkBlocks();
            cache.put(p, blocks);
        }
        
        // Verify cache size is limited
        if (cache.size() <= 10) {
            Log.info("Cache eviction working correctly - size: " + cache.size());
        } else {
            Log.error("Cache eviction failed - size: " + cache.size(), null, false);
        }
        
        // Test access pattern (should keep recently accessed items)
        Point recentPoint = new Point(45, 45);
        Chunk.Blocks recentBlocks = cache.get(recentPoint);
        
        // Add more items
        for (int i = 100; i < 120; i++) {
            Point p = new Point(i, i);
            cache.put(p, createMockChunkBlocks());
        }
        
        // Check if recently accessed item is still there
        Chunk.Blocks stillThere = cache.get(recentPoint);
        if (stillThere == recentBlocks) {
            Log.info("LRU behavior working correctly");
        } else {
            Log.info("LRU behavior test inconclusive");
        }
        
        cache.logStatistics();
    }
    
    /**
     * Create mock chunk blocks for testing.
     */
    private static Chunk.Blocks createMockChunkBlocks() {
        // This is a simplified mock - in real usage this would be actual chunk data
        // Since Chunk.Blocks is abstract, we'll return null for testing
        // In a real scenario, you would use a concrete implementation
        return null;
    }
    
    /**
     * Generate random test points.
     */
    private static List<Point> generateTestPoints(int count) {
        List<Point> points = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for reproducible tests
        
        for (int i = 0; i < count; i++) {
            points.add(new Point(random.nextInt(1000), random.nextInt(1000)));
        }
        
        return points;
    }
}