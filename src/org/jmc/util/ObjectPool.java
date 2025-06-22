package org.jmc.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Generic object pool for reducing garbage collection pressure.
 * 
 * @param <T> Type of objects to pool
 */
public class ObjectPool<T extends ObjectPool.Recyclable> {
    
    /**
     * Interface for objects that can be recycled in the pool.
     */
    public interface Recyclable {
        /**
         * Reset the object to its initial state for reuse.
         */
        void reset();
    }
    
    private final BlockingQueue<T> pool;
    private final Supplier<T> factory;
    private final int maxPoolSize;
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final AtomicInteger borrowCount = new AtomicInteger(0);
    private final AtomicInteger returnCount = new AtomicInteger(0);
    private final AtomicInteger createCount = new AtomicInteger(0);
    
    /**
     * Creates a new object pool.
     * 
     * @param factory Function to create new objects when pool is empty
     * @param maxSize Maximum number of objects to keep in pool
     */
    public ObjectPool(Supplier<T> factory, int maxSize) {
        this.factory = factory;
        this.maxPoolSize = maxSize;
        this.pool = new LinkedBlockingQueue<>(maxSize);
    }
    
    /**
     * Borrows an object from the pool, creating a new one if pool is empty.
     */
    public T borrow() {
        T object = pool.poll();
        borrowCount.incrementAndGet();
        
        if (object != null) {
            currentSize.decrementAndGet();
            object.reset();
            return object;
        }
        
        // Pool is empty, create new object
        createCount.incrementAndGet();
        return factory.get();
    }
    
    /**
     * Returns an object to the pool for reuse.
     */
    public void returnObject(T object) {
        if (object == null) {
            return;
        }
        
        returnCount.incrementAndGet();
        
        // Only return to pool if there's space
        if (currentSize.get() < maxPoolSize && pool.offer(object)) {
            currentSize.incrementAndGet();
        }
        // If pool is full, object will be garbage collected
    }
    
    /**
     * Clears all objects from the pool.
     */
    public void clear() {
        pool.clear();
        currentSize.set(0);
    }
    
    /**
     * Returns current pool size.
     */
    public int size() {
        return currentSize.get();
    }
    
    /**
     * Returns pool utilization statistics.
     */
    public String getStatistics() {
        int borrowed = borrowCount.get();
        int returned = returnCount.get();
        int created = createCount.get();
        int pooled = borrowed - returned;
        
        return String.format(
            "Pool Stats - Size: %d/%d, Borrowed: %d, Returned: %d, Created: %d, In Use: %d",
            currentSize.get(), maxPoolSize, borrowed, returned, created, pooled
        );
    }
}