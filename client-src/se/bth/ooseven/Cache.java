package se.bth.ooseven;

import se.sics.tac.solver.FastOptimizer;

import java.util.Comparator;
import java.util.NoSuchElementException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;

import java.util.logging.Level;
import java.util.logging.Logger;


public class Cache {
    //
    //  Instance variables
    //

    // Constants for data structure sizes
    private final int SIZE;
    private final int BUFFER;
    private final int FAST_SIZE;
    
    // Customer preferences -> used for running the Solver
    private final Preferences prefs;
    
    // Storage data structures
    private final ConcurrentHashMap<Owns, Entry> storage;
    private final ConcurrentSkipListSet<Entry> fastest;
    
    // Buffer data structures for the Manager
    private final ConcurrentHashMap<Owns, Entry> buffer;
    private final LinkedBlockingQueue<Entry> todo;
    
    // Indicator for the remover to run or wait
    private final Semaphore runRemover = new Semaphore(0);
    
    // Background threads; Flag for Threads to run/stop
    private final Thread manager, remover;
    private boolean run = true;

    // Statistics
    private Statistics stats = new Statistics();

    
    /*
     * These two variables are used to mark witch entries have not been used for
     * two runs in a row. LastRun just stores the start time of this round and
     * is later transfered to killOlder.
     *
     * All entries in older than killOlder are removed from the cache.
     */
    private long lastRun   = System.nanoTime();
    private long killOlder = 0;
    
    //
    //  Constructors
    //
    
    /**
     * Sets up the cache. Starts threads for background cache management. The 
     * size of the cache will be 1 Mega Entry.
     *
     * @param prefs     The preferences of the clients
     */
    public Cache(Preferences prefs) {
        this(prefs, 1024*1024); // 1 Mega Entries
    }
    
    /**
     * Sets up the cache. Starts threads for background cache management. The
     * values for buffer and fast sizes are used relative to the size:
     * Buffer   <- size/100, but minimum of 10
     * FastSize <- size/10,  but minimum of 10
     *
     * @param prefs     The preferences of the clients
     * @param size      Size of the storage in this cache
     */
    public Cache(Preferences prefs, int size) {
        this(prefs, size, size > 1000 ? size/100 : 10, size > 100 ? size/10 : 10);
    }
    
    /**
     * Sets up the cache. Starts threads for background cache management.
     * @param prefs     The preferences of the clients
     * @param size      Size of the storage in this cache
     * @param buffer    Size of the buffer for new elements to be added
     * @param fastSize  Size of the lookup Tree with the fastest elements
     */
    public Cache(Preferences prefs, int size, int buffer, int fastSize) {
        SIZE      = size;
        BUFFER    = buffer;
        FAST_SIZE = fastSize;
    
        this.prefs = prefs;
        
        this.storage = new ConcurrentHashMap<>(SIZE);
        this.buffer  = new ConcurrentHashMap<>(BUFFER);
        this.todo    = new LinkedBlockingQueue<>(BUFFER - 1);
        this.fastest = new ConcurrentSkipListSet<>(new FasterEntry());
        
        manager = new Thread(new Manager(), "Cache.Manager");
        remover = new Thread(new Remover(), "Cache.Remover");
        
        manager.setPriority(Thread.MIN_PRIORITY + 1);
        remover.setPriority(Thread.MIN_PRIORITY);
        
        manager.start();
        remover.start();
    }
    
    //
    //  External interface
    //
    
    /**
     * Looks up the result in the cache and returns it if found. Otherwise runs
     * the calculation and updates the cache.
     */
    public int calc(Owns owns) {
        Entry entry = lookup(owns);
        
        if(entry == null) {
            stats.miss.incrementAndGet();
            entry = run(owns);
            store(entry);
        } else {
            stats.hits.incrementAndGet();
            entry.used();
        }

        return entry.result;
    }
    
    /**
     *  This function tells the Cache that a new run has begun and that all
     *  entries from two rounds ago, which have not been used can get deleted.
     */
    public void removeOld() {
        killOlder = lastRun;
        lastRun = System.nanoTime();
        
        // Start remover for cleanup
        runRemover.release();
    }
    
    //
    //  Internal Helper
    //
    
    /**
     *  Helper function for looking up entries in the cache.
     */
    private Entry lookup(Owns owns) {
        Entry entry = buffer.get(owns);
        if(entry == null) {
            entry = storage.get(owns);
        }
        return entry;
    }
    
    /**
     *  Helper function for running the calculation
     */
    private Entry run(Owns owns) {   
        int result;
        long time;
        
        // Change the log level of the FastOptimizer's Logger to avoid spam.
        Logger log = Logger.getLogger(FastOptimizer.class.getName());
        if (log != null) {
            log.setLevel(Level.INFO);
        } else {
            System.err.println("Failed to find the FastOptimizer's Logger.");
        }
        
        FastOptimizer fo = new FastOptimizer();
        fo.setClientData(prefs.getSolverFormat(), owns.getSolverFormat());
        
        long start = System.nanoTime();
        result = fo.solve();
        time = System.nanoTime() - start;
        
        Entry entry = new Entry(owns, result, time);
        
        stats.calctime.addAndGet(entry.time);
        
        return entry;
    }
    
    /**
     *  Helper function for storing a new entry in the cache and performing
     *  needed cache maintenance in background. (Removing old entries)
     *
     *  This function blocks if the to-do-list for the cache manager is full.
     */
    private void store(Entry e) {
        long start_wait = System.nanoTime();
        try {
            // Send item to Cache-Manager
            buffer.put(e.base, e);
            todo.put(e);
        } catch (InterruptedException ex) {
            // If something goes wrong here, we just drop the entry
            System.err.println("Cache: Got interrupted while waiting to put something in the cache!");
            
            // Clean up
            todo.remove(e);
            buffer.remove(e.base);
        }
        stats.waittime.addAndGet(System.nanoTime() - start_wait);
    }
    
    //
    //  Entries and Entry-Helper
    //
    
    /**
     *  This class represents one entry in the cache, it only sticks the extra
     *  information to the Owns base.
     */
    private class Entry {
        public final Owns base;
        public final int result;
        public final long time;
        public long lastUsed;
        
        /**
         *  Constructor for the entry, just stores the given information.
         */
        public Entry(Owns base, int result, long time) {
            this.base = base;
            this.result = result;
            this.time = time;
            this.used();
        }
        
        /**
         *  Updates the last-use timestamp in a Thread-safe way.
         */
        public synchronized void used() {
            lastUsed = System.nanoTime();
        }
        
        /**
         *  Helper for storing in a HashMap.
         */
        public int hashCode() {
            return base.hashCode();
        }
        
        /**
         *  Helper for storing in a TreeSet.
         */
        public boolean equals(Entry other) {
            return this.base.equals(other.base);
        }
    }
    
    /**
     *  Comparator for sorting entries by time.
     */
    private class FasterEntry implements Comparator<Entry> {
        @Override
        public int compare(Entry e1, Entry e2) {
            return Long.compare(e1.time, e2.time);
        }
    }
    
    //
    //  Threads and Thread-Helper
    //
    
    /**
     * Stops the manager and remover for this cache.
     */
    public void stop() {
        run = false;
        
        manager.interrupt();
        remover.interrupt();
    }
    
    /**
     *  Waits for manager and remover to be done with their work. Mainly used
     *  for getting stats that contain all pending actions.
     */
    public void waitForThreads() {
        while(manager.getState().equals(Thread.State.RUNNABLE) ||
              remover.getState().equals(Thread.State.RUNNABLE)) {
            Thread.yield();
        }
    }
    
    /**
     *  Runnable for the cache manager.
     *
     *  The cache manager takes care of putting new entries in the cache. It also
     *  takes care, for removing faster entries if there is not enough room in
     *  the cache left.
     *
     *  The thread blocks as soon as there are no more entries on the to-do-list.
     */
    private class Manager implements Runnable {
        public void run() {
            while(run) {
                try {
                    // Get new entry from the list
                    Entry entry = todo.take();
                    
                    // Get room for entry
                    if(storage.size() >= SIZE) {
                        Entry candidate;
                        
                        // Search slowest entry
                        candidate = fastest.pollFirst();
                        
                        // If there was no fast entry
                        if(candidate == null) {
                            stats.nofast.incrementAndGet();
                            
                            // Choose any element from the storage
                            // Workaround -> Should rarely happen
                            candidate = storage.values().iterator().next();
                        }
                        
                        // If the new entry is faster than all others -> abort!
                        if(candidate != null && entry.time <= candidate.time) {
                            continue;
                        }
                        
                        if(candidate != null) {                       
                            // Remove from storage and fastest
                            fastest.remove(candidate);
                            storage.remove(candidate.base);
                            
                            // Update statistics
                            stats.removals.incrementAndGet();
                            
                            // Tell remover to start if fastest is half empty
                            if(fastest.size() < FAST_SIZE/2 && fastest.size() < storage.size()) {
                                runRemover.release();
                            }
                        } else {
                            System.err.println("Cache: Still no candidate! This should never happen!");
                        }
                    }
                    
                    // Put into storage
                    storage.put(entry.base, entry);
                    fastest.add(entry);
                                 
                    // Remove from buffer
                    buffer.remove(entry.base);
                    
                    // Update statistics
                    stats.adds.incrementAndGet();
                    
                    // Adjust size of fast-list
                    if(fastest.size() >= FAST_SIZE) {
                        fastest.pollLast();
                    }
                } catch (InterruptedException e) {
                    System.err.println("Cache.Manager got interrupted!");
                }
            }
        }
    }
    
    /**
     *  Runnable for the remover thread.
     *
     *  This thread periodically walks over the whole storage, to find the
     *  fastest calculations and to remove any old entries.
     *
     *  It will wait on the runRemover Semaphore. So after significant changes
     *  to the storage runRemover.release() should be called to start the
     *  remover. Multiple calls to rundercover.release() will only cause one
     *  complete run.
     */
    private class Remover implements Runnable {
        public void run() {
            while(run) {
                try {
                    // Wait until something changed
                    int permits = runRemover.availablePermits();
                    if(permits == 0){
                        permits = 1;
                    }
                    runRemover.acquire(permits);
                
                    /* 
                     * fast_time is updated in intervals and denotes the slowest
                     * time in the fastest list. This way we don't need to call
                     * fastest.last() all the time.
                     * last_lookup is used to store when we last looked for a
                     * new value for fast_time
                     */
                    long fast_time = Long.MAX_VALUE;
                    long last_lookup = 0;
                    
                    // Walk over all storage entries
                    for(Entry e : storage.values()) {
                        // Remove old entries
                        if(e.lastUsed < killOlder) {
                            fastest.remove(e);
                            storage.remove(e.base);
                            stats.removals.incrementAndGet();
                            continue;
                        }
                        
                        // Update fast_time every 10ms
                        if(System.nanoTime() - last_lookup > 1000*1000*10) { 
                            last_lookup = System.nanoTime();
                            
                            try {
                                fast_time = fastest.last().time;
                            } catch (NoSuchElementException ex) {
                                fast_time = Long.MAX_VALUE;
                            }                            
                        }
                        
                        // Fill fastest
                        if(e.time < fast_time) {
                            fastest.add(e);
                        }
                        
                        // Check for maximum size of fastest
                        while(fastest.size() >= FAST_SIZE) {
                            fastest.pollLast();
                        }
                    }
                } catch (InterruptedException ex) {
                    System.err.println("Cache.Remover got interrupted!");
                }
            }
        }
    }
    
    //
    // Statistics
    //
    
    /**
     *  Helper for accessing the Statistics.
     */
    public Statistics getStatistics() {
        return stats;
    }
    
    /**
     *  Helper for reseting the Statistics.
     */
    public void resetStats() {
        stats.reset();
    }
    
    /**
     *  Class for storing statistics about the cache.
     */
    public class Statistics {
        // Counter
        public final AtomicInteger hits     = new AtomicInteger();
        public final AtomicInteger miss     = new AtomicInteger();
        public final AtomicInteger removals = new AtomicInteger();
        public final AtomicInteger adds     = new AtomicInteger();
        public final AtomicInteger nofast   = new AtomicInteger();
        
        // Timer
        public final AtomicLong calctime    = new AtomicLong();
        public final AtomicLong waittime    = new AtomicLong();
        
        public Statistics() {
            reset();
        }
        
        // Copy-constructor
        public Statistics(Statistics other) {
            this.hits.set(other.hits.get());
            this.miss.set(other.miss.get());
            this.removals.set(other.removals.get());
            this.adds.set(other.adds.get());
            this.nofast.set(other.nofast.get());
            
            this.calctime.set(other.calctime.get());
            this.waittime.set(other.waittime.get());
        }
        
        // Reset all timers and counters back to zero
        public void reset() {
            hits.set(0);
            miss.set(0);
            removals.set(0);
            adds.set(0);
            nofast.set(0);
            calctime.set(0);
            waittime.set(0);
        }
        
        // Return a nicely formated string showing the statistics
        public String toString() {
            int hits = this.hits.intValue();
            int miss = this.miss.intValue();
            double hitRate  = 100.0*hits/(hits+miss);
            double missRate = 100.0*miss/(hits+miss);
            
            int removals = this.removals.intValue();
            int adds     = this.adds.intValue();
            int nofast   = this.nofast.intValue();
            double nofastRate = adds != 0 ? 100*nofast/adds : 100;        
            
            long calctime = this.calctime.longValue()/(1000*1000); // ms
            long avgCalctime = miss != 0 ? (this.calctime.longValue()/miss)/(1000) : -1; // us
            
            long waittime = this.waittime.longValue()/(1000*1000); // ms
            long avgWaittime = adds != 0 ? (this.waittime.longValue()/adds)/(1000) : -1; // us
            
        
            String ret = "";
            ret += "Hits:   "+hits+"\t("+hitRate+"%)\n";
            ret += "Misses: "+miss+"\t("+missRate+"%)\n";
            ret += "Fill:   "+storage.size()+"\t("+(double) (100*storage.size()/SIZE)+"%)\n";
            ret += "Adds:   "+adds+"\n";
            ret += "NoFast: "+nofast+"\t("+nofastRate+"%)\n";
            ret += "Rm.s:   "+removals+"\n";
            ret += "Calc:   "+calctime+" ms\t(Avg: "+avgCalctime+" us)\n";
            ret += "Wait:   "+waittime+" ms\t(Avg: "+avgWaittime+" us)\n";
            
            return ret;
        }
        
        /**
         * @returns The statistics as machine readable comma separated values.
         */
        public String toCSV() {
            return ""+hits+","+miss+","+storage.size()+","+adds+","+nofast+","+removals+","+calctime+","+waittime+"";
        }
    }
}

