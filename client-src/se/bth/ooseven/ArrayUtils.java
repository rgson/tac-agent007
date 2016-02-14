package se.bth.ooseven;

/**
 * A utility class for array manipulation.
 */
public abstract class ArrayUtils {

    /**
     * Copies an array of integers.
     * @param original The original to be copied.
     * @return The copy.
     */
    public static int[] copyArray(int[] original) {
        int[] copy = new int[original.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = original[i];
        }
        return copy;
    }

    /**
     * Copies a 2D array of integers.
     * @param original The original to be copied.
     * @return The copy.
     */
    public static int[][] copyArray(int[][] original) {
        int[][] copy = new int[original.length][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = new int[original[i].length];
            for (int j = 0; j < copy[i].length; j++) {
                copy[i][j] = original[i][j];
            }
        }
        return copy;
    }

}
