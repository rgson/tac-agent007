package se.rgson.util;

/**
 * A stopwatch utility for measuring runtime in nanoseconds.
 * Allows for nested measurements in a thread-safe manner.
 *
 * @author Robin Gustafsson
 */
public final class Stopwatch {

    /**
     * Holds one InnerStopwatch instance for each accessing thread.
     */
    private static ThreadLocal<InnerStopwatch> stopwatch = new ThreadLocal<InnerStopwatch>() {
        @Override
        protected InnerStopwatch initialValue() {
            return new InnerStopwatch();
        }
    };

    /**
     * Private constructor to hinder instantiation.
     * The class is supposed to be used in a static manner.
     */
    private Stopwatch() {
    }

    /**
     * Starts a measurement.
     */
    public static void start() {
        stopwatch.get().start();
    }

    /**
     * Ends the most recently started measurement.
     *
     * @return The time elapsed since the clock was started.
     */
    public static long stop() {
        return stopwatch.get().stop();
    }

    /**
     * The implementation of the actual stopwatch functionality.
     * Allows for nested measurements.
     */
    private static class InnerStopwatch {

        /**
         * The start times of not-yet-stopped measurements.
         */
        private long[] startTimes = new long[3];

        /**
         * The next index to start the next run on.
         */
        private int nextIndex = 0;

        /**
         * Starts a measurement.
         */
        public void start() {
            if (startTimes.length < nextIndex) {
                increaseSize();
            }
            startTimes[nextIndex++] = System.nanoTime();
        }

        /**
         * Ends the most recently started measurement.
         *
         * @return The time elapsed since the clock was started.
         */
        public long stop() {
            long stop = System.nanoTime();
            long start = startTimes[--nextIndex];
            return stop - start;
        }

        /**
         * Increases the size of the time array.
         */
        private void increaseSize() {
            long[] newArray = new long[startTimes.length * 2];
            System.arraycopy(startTimes, 0, newArray, 0, startTimes.length);
            startTimes = newArray;
        }

    }

}
