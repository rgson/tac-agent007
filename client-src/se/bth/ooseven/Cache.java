package se.bth.ooseven;

import se.sics.tac.solver.FastOptimizer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;

public class Cache {
    private static final int SIZE   = 102400;
    private static final int BUFFER =   1024;

    private final Preferences prefs;
    
    private final ConcurrentHashMap<Owns, Entry> storage;
    private final ConcurrentHashMap<Owns, Entry> buffer;
    private final LinkedBlockingQueue<Entry> todo;
    private final LinkedBlockingQueue<Entry> remove;
    
    private final Thread manager, remover;

    private AtomicInteger hits = new AtomicInteger();
    private AtomicInteger miss = new AtomicInteger();
    
    private boolean run = true;
    
    /**
     * Sets up the cache. Starts threads for background cache management.
     */
    public Cache(Preferences prefs) {
        this.prefs = prefs;
        
        this.storage = new ConcurrentHashMap<>(SIZE);
        this.buffer  = new ConcurrentHashMap<>(BUFFER);
        this.todo    = new LinkedBlockingQueue(BUFFER - 1);
        this.remove  = new LinkedBlockingQueue(16);
        
        manager = new Thread(new Manager(), "Cache.Manager");
        remover = new Thread(new Remover(), "Cache.Remover");
        
        manager.setPriority(Thread.MIN_PRIORITY + 1);
        remover.setPriority(Thread.MIN_PRIORITY);
        
        manager.start();
        remover.start();
    }
    
    /**
     * Stops the managers for this cache.
     */
    public void stop() {
        run = false;
    }
    
    /**
     * Looksup the result in the cache and returns it if found. Otherwise runs
     * the calculation and updates the cache.
     */
    public int calc(Owns owns) {
        Entry entry = lookup(owns);
        if(entry == null) {
            miss.incrementAndGet();
            entry = run(owns);
            store(entry);
        } else {
            hits.incrementAndGet();
        }
        
        entry.lastUsed = new Long(System.nanoTime());
        
        return entry.result;
    }
    
    /**
     * Helper function for looking up entries in the cache.
     */
    private Entry lookup(Owns owns) {
        Entry entry = buffer.get(owns);
        if(entry == null) {
            entry = storage.get(owns);
        }
        return entry;
    }
    
    /**
     * Helper function for running the calculation using a Threadpool in the
     * background.
     */
    private Entry run(Owns owns) {
        // TODO Queue -> Threadpool
    
        Entry entry = new Entry();
        entry.base = new Owns(owns);
        
        FastOptimizer fo = new FastOptimizer();
        fo.setClientData(prefs.getSolverFormat(), owns.getSolverFormat());
        
        long start = System.nanoTime();
        entry.result = fo.solve();
        entry.time = System.nanoTime() - start;
        
        return entry;
    }
    
    /**
     * Helper function for storing a new entry in the cache and performing
     * needed cache maintanance in background. (Removing old entries)
     *
     * This function blocks if the todo-list for the cache manager is full.
     */
    private void store(Entry e) {
        // TODO Queue -> Cachemaster Thread
        
        if(storage.size() >= SIZE) {
            // TODO Put items on Todolist and buffer
            // buffer.put(e.base, e);
            // todo.put(e);
        } else {
            storage.put(e.base, e);
        }
    }
    
    private class Entry {
        public Owns base;
        public int result;
        public long time;
        public Long lastUsed;
    }
    
    private class Manager implements Runnable {
        public void run() {
            try {
                while(run) {
                    Entry entry = todo.take();
                    
                    // Get room for entry
                    
                    // Put into storage
                    
                    // Remove from buffer
                }
            } catch (InterruptedException e) {
                if(run)
                    System.err.println("Cache.Manager got interrupted, End!");
            }
        }
    }
    
    private class Remover implements Runnable {
        public void run() {
            try {
                while(run) {
                    // Walk over storage search for old entries
                    Entry old = new Entry();
                    
                    remove.put(old);
                }
            } catch (InterruptedException e) {
                if(run)
                    System.err.println("Cache.Remover got interrupted, End!");
            }
        }
    }
}

