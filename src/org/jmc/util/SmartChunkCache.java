package org.jmc.util;

import java.awt.Point;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.CheckForNull;

import org.jmc.Chunk;

/**
 * Smart caching system that automatically manages memory usage and evicts
 * entries when memory pressure is high.
 * 
 * Features:
 * - Automatic memory pressure detection
 * - LRU-based eviction policy
 * - Thread-safe operations
 * - Memory usage statistics
 */
public class SmartChunkCache {
    
    // Memory thresholds
    private static final double HIGH_MEMORY_THRESHOLD = 0.8; // 80%
    private static final double CRITICAL_MEMORY_THRESHOLD = 0.9; // 90%
    
    // Cache configuration
    private final int maxCacheSize;
    private final int lowWaterMark; // Size to reduce to when cleaning
    
    // Thread-safe LRU cache
    private final Map<Point, Chunk.Blocks> cache = new ConcurrentHashMap<>();
    private final LinkedHashMap<Point, Long> accessOrder = new LinkedHashMap<Point, Long>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Point, Long> eldest) {
            if (size() > maxCacheSize) {
                Point key = eldest.getKey();
                cache.remove(key);
                cacheEvictions.incrementAndGet();
                return true;
            }
            return false;
        }
    };
    
    // Memory monitoring
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    private final AtomicLong memoryCleanups = new AtomicLong(0);
    
    public SmartChunkCache(int maxSize) {
        this.maxCacheSize = maxSize;
        this.lowWaterMark = Math.max(1, maxSize / 2);
    }
    
    /**
     * Gets a chunk from cache, returns null if not found.
     */
    @CheckForNull
    public Chunk.Blocks get(Point coord) {
        Chunk.Blocks blocks = cache.get(coord);
        
        if (blocks != null) {
            // Update access order
            synchronized (accessOrder) {
                accessOrder.put(coord, System.nanoTime());
            }
            cacheHits.incrementAndGet();
            return blocks;
        }
        
        cacheMisses.incrementAndGet();
        return null;
    }
    
    /**
     * Puts a chunk into cache with memory pressure checking.
     */
    public void put(Point coord, Chunk.Blocks blocks) {
        if (blocks == null) {
            return;
        }
        
        // Check memory pressure before adding
        if (shouldCleanMemory()) {
            performMemoryCleanup();
        }
        
        cache.put(coord, blocks);
        synchronized (accessOrder) {
            accessOrder.put(coord, System.nanoTime());
        }
    }
    
    /**
     * Removes a specific entry from cache.
     */
    public void remove(Point coord) {
        cache.remove(coord);
        synchronized (accessOrder) {
            accessOrder.remove(coord);
        }
    }
    
    /**
     * Clears all cache entries.
     */
    public void clear() {
        cache.clear();
        synchronized (accessOrder) {
            accessOrder.clear();
        }
        Log.debug("Cache cleared manually");
    }
    
    /**
     * Checks if memory cleanup should be performed.
     */
    private boolean shouldCleanMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        return memoryUsageRatio > HIGH_MEMORY_THRESHOLD;
    }
    
    /**
     * Performs memory cleanup by evicting least recently used entries.
     */
    private void performMemoryCleanup() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        int targetSize;
        if (memoryUsageRatio > CRITICAL_MEMORY_THRESHOLD) {
            // Critical situation - clear 75% of cache
            targetSize = Math.max(1, cache.size() / 4);
            Log.info("Critical memory usage detected (" + String.format("%.1f", memoryUsageRatio * 100) + 
                    "%), aggressive cache cleanup initiated");
        } else {
            // High memory usage - reduce to low water mark
            targetSize = lowWaterMark;
            Log.debug("High memory usage detected (" + String.format("%.1f", memoryUsageRatio * 100) + 
                     "%), cache cleanup initiated");
        }
        
        // Remove oldest entries until we reach target size
        synchronized (accessOrder) {
            while (cache.size() > targetSize && !accessOrder.isEmpty()) {
                Point oldestKey = accessOrder.keySet().iterator().next();
                cache.remove(oldestKey);
                accessOrder.remove(oldestKey);
                cacheEvictions.incrementAndGet();
            }
        }
        
        memoryCleanups.incrementAndGet();
        
        // Suggest GC after major cleanup (but don't force it)
        if (memoryUsageRatio > CRITICAL_MEMORY_THRESHOLD) {
            System.gc();
        }
        
        Log.debug("Cache cleanup completed. New size: " + cache.size());
    }
    
    /**
     * Returns current cache size.
     */
    public int size() {
        return cache.size();
    }
    
    /**
     * Returns cache hit ratio.
     */
    public double getHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        return total == 0 ? 0.0 : (double) hits / total;
    }
    
    /**
     * Returns cache statistics as formatted string.
     */
    public String getStatistics() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        return String.format(
            "Cache Stats - Size: %d/%d, Hits: %d, Misses: %d, Hit Ratio: %.2f%%, " +
            "Evictions: %d, Memory Cleanups: %d, Memory Usage: %.1f%%",
            cache.size(), maxCacheSize,
            cacheHits.get(), cacheMisses.get(), getHitRatio() * 100,
            cacheEvictions.get(), memoryCleanups.get(), memoryUsageRatio * 100
        );
    }
    
    /**
     * Logs current cache statistics.
     */
    public void logStatistics() {
        Log.info(getStatistics());
    }
}