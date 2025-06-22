package org.jmc.util;

import org.jmc.Chunk;

/**
 * Specialized object pool for Chunk.Blocks objects to reduce GC pressure
 * during chunk processing.
 */
public class ChunkDataPool {
    
    private final ObjectPool<PooledChunkBlocks> pool;
    
    /**
     * Wrapper for Chunk.Blocks that implements Recyclable interface.
     */
    public static class PooledChunkBlocks implements ObjectPool.Recyclable {
        private Chunk.Blocks blocks;
        
        public PooledChunkBlocks() {
            // Will be set when first used
            this.blocks = null;
        }
        
        public void setBlocks(Chunk.Blocks blocks) {
            this.blocks = blocks;
        }
        
        public Chunk.Blocks getBlocks() {
            return blocks;
        }
        
        @Override
        public void reset() {
            // Clear references to allow GC of underlying data
            this.blocks = null;
        }
        
        /**
         * Returns true if this wrapper contains valid blocks data.
         */
        public boolean hasBlocks() {
            return blocks != null;
        }
    }
    
    /**
     * Creates a new chunk data pool.
     * 
     * @param maxSize Maximum number of chunk block objects to pool
     */
    public ChunkDataPool(int maxSize) {
        this.pool = new ObjectPool<>(PooledChunkBlocks::new, maxSize);
    }
    
    /**
     * Borrows a pooled chunk blocks wrapper from the pool.
     */
    public PooledChunkBlocks borrow() {
        return pool.borrow();
    }
    
    /**
     * Returns a chunk blocks wrapper to the pool for reuse.
     */
    public void returnObject(PooledChunkBlocks pooledBlocks) {
        if (pooledBlocks != null) {
            pool.returnObject(pooledBlocks);
        }
    }
    
    /**
     * Convenience method to create a pooled blocks object with data.
     */
    public PooledChunkBlocks createPooled(Chunk.Blocks blocks) {
        PooledChunkBlocks pooled = borrow();
        pooled.setBlocks(blocks);
        return pooled;
    }
    
    /**
     * Clears the pool.
     */
    public void clear() {
        pool.clear();
    }
    
    /**
     * Returns pool statistics.
     */
    public String getStatistics() {
        return pool.getStatistics();
    }
    
    /**
     * Logs pool statistics.
     */
    public void logStatistics() {
        Log.debug("Chunk Data " + getStatistics());
    }
}