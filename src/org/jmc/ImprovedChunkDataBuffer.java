package org.jmc;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.jmc.Chunk.Blocks;
import org.jmc.util.CachedGetter;
import org.jmc.util.ChunkDataPool;
import org.jmc.util.Log;
import org.jmc.util.SmartChunkCache;

import javax.annotation.CheckForNull;

/**
 * Improved chunk data buffer that uses smart caching and memory pooling
 * to reduce garbage collection pressure and improve performance.
 * 
 * Extends ChunkDataBuffer to maintain compatibility with existing code.
 */
public class ImprovedChunkDataBuffer extends ChunkDataBuffer {

    private final SmartChunkCache chunkCache;
    private final ChunkDataPool dataPool;
    private final MemoryMXBean memoryBean;
    
    // Configuration
    private static final int DEFAULT_CACHE_SIZE = 200; // Chunks to cache
    private static final int DEFAULT_POOL_SIZE = 50;   // Pooled objects
    
    public ImprovedChunkDataBuffer(int xmin, int xmax, int ymin, int ymax, int zmin, int zmax) {
        super(xmin, xmax, ymin, ymax, zmin, zmax);
        
        // Calculate optimal cache size based on available memory
        long availableMemory = Runtime.getRuntime().maxMemory();
        int optimalCacheSize = calculateOptimalCacheSize(availableMemory);
        
        this.chunkCache = new SmartChunkCache(optimalCacheSize);
        this.dataPool = new ChunkDataPool(DEFAULT_POOL_SIZE);
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        
        Log.info(String.format("Initialized improved chunk buffer - Cache size: %d, Pool size: %d, Available memory: %dMB",
                optimalCacheSize, DEFAULT_POOL_SIZE, availableMemory / 1024 / 1024));
    }
    
    /**
     * Calculate optimal cache size based on available memory.
     */
    private int calculateOptimalCacheSize(long availableMemory) {
        // Estimate 2MB per cached chunk
        long estimatedChunkSize = 2 * 1024 * 1024;
        
        // Use up to 25% of available memory for chunk cache
        long cacheMemory = availableMemory / 4;
        int calculatedSize = (int) (cacheMemory / estimatedChunkSize);
        
        // Clamp between reasonable bounds
        return Math.max(50, Math.min(calculatedSize, 1000));
    }
    
    /**
     * Remove all cached chunks and clear pools.
     */
    @Override
    public synchronized void removeAllChunks() {
        super.removeAllChunks();
        chunkCache.clear();
        dataPool.clear();
        
        Log.debug("All chunks removed from improved buffer");
    }
    
    /**
     * Get current number of cached chunks.
     */
    @Override
    public synchronized int getChunkCount() {
        return super.getChunkCount() + chunkCache.size();
    }

    /**
     * Get blocks for a chunk, using smart caching.
     */
    @Override
    public Blocks getBlocks(Point p) {
        // Try cache first
        Blocks cachedBlocks = chunkCache.get(p);
        if (cachedBlocks != null) {
            return cachedBlocks;
        }
        
        // Cache miss - use parent implementation and cache result
        Blocks blocks = super.getBlocks(p);
        if (blocks != null) {
            // Add to cache
            chunkCache.put(p, blocks);
        }
        
        return blocks;
    }
    
    /**
     * Get current memory usage statistics.
     */
    public String getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        return String.format("Buffer Memory - Heap Usage: %.1f%%, %s, %s",
                memoryUsageRatio * 100,
                chunkCache.getStatistics(),
                dataPool.getStatistics());
    }
    
    /**
     * Log detailed performance statistics.
     */
    public void logPerformanceStats() {
        Log.info(getMemoryStats());
        chunkCache.logStatistics();
        dataPool.logStatistics();
    }
    
    /**
     * Perform maintenance operations (cleanup, optimization).
     */
    public void performMaintenance() {
        // Log current stats
        logPerformanceStats();
        
        // Check if we need to clear cache due to memory pressure
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        if (memoryUsageRatio > 0.85) { // 85% memory usage
            Log.info("High memory usage detected during maintenance, clearing caches");
            removeAllChunks();
        }
    }
    
    // XZ and XY boundaries are inherited from parent class
}