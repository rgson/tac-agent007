package se.bth.ooseven;

/**
 * Represents a collection of owned items.
 *
 * Offers convenience methods for adding, setting and getting the number of
 * owned items.
 *
 * Also offers conversions between the agent's format and the solver's format.
 */
public class Owns {

    /*
     * The agent's format:
     * [ 0 -  3] Inflights
     * [ 4 -  7] Outflights
     * [ 8 - 11] Cheap hotels
     * [12 - 15] Good hotels
     * [16 - 19] Alligator wrestling
     * [20 - 23] Amusement
     * [24 - 27] Museum
     *
     * The solver's format:
     * [0][0 - 3] Inflights
     * [1][0 - 3] Outflights
     * [2][0 - 3] Cheap hotels
     * [3][0 - 3] Good hotels
     * [4][0 - 3] Alligator wrestling
     * [5][0 - 3] Amusement
     * [6][0 - 3] Museum
     */

    /**
     * The owned items, stored in the solver's format.
     */
    private final int[][] ownedItems;

    /**
     * Constructs an Owns object from the agent's format.
     * @param owns The agent's format representation of owned items.
     */
    public Owns(int[] owns) {
        this.ownedItems = convert(owns);
    }

    /**
     * Constructs an Owns object from the solver's format.
     * @param owns The solver's format representation of owned items.
     */
    public Owns(int[][] owns) {
        this.ownedItems = ArrayUtils.copyArray(owns);
    }

    /**
     * Constructs an Owns object as a copy of another.
     * @param owns The Owns object to copy.
     */
    public Owns(Owns owns) {
        this.ownedItems = ArrayUtils.copyArray(owns.ownedItems);
    }

    /**
     * Converts the agent's format into the solver's format.
     * @param owns The agent's format representation of owned items.
     * @return The solver's format representation of owned items.
     */
    private int[][] convert(int[] owns) {
        int[][] converted = new int[7][];
        for (int i = 0; i < converted.length; i++) {
            converted[i] = new int[4];
            for (int j = 0; j < converted[i].length; j++) {
                converted[i][j] = owns[i * 4 + j];
            }
        }
        return converted;
    }

    /**
     * Converts the solver's format into the agent's format.
     * @param owns The solver's format representation of owned items.
     * @return The agent's format representation of owned items.
     */
    private int[] convert(int[][] owns) {
        int[] converted = new int[28];
        for (int i = 0; i < owns.length; i++) {
            for (int j = 0; j < owns[i].length; j++) {
                converted[i * 4 + j] = owns[i][j];
            }
        }
        return converted;
    }

    /**
     * Returns the owned items in the agent's format.
     * @return The owned items.
     */
    public int[] getAgentFormat() {
        return convert(this.ownedItems);
    }

    /**
     * Returns the owned items in the solver's format.
     * @return The owned items.
     */
    public int[][] getSolverFormat() {
        // TODO skip the copy? possibly redundant and costs memory/performance.
        return ArrayUtils.copyArray(this.ownedItems);
    }

    /**
     * Adds an item to the owned items.
     *
     * @param item The item to add a copy to.
     * @param quantity The quantity to add.
     */
    public void add(Item item, int quantity) {
        this.ownedItems[item.type.index][item.index] += quantity;
    }

    /**
     * Gets the number of owned copies of the specified item.
     *
     * @param item The item to get the count for.
     */
    public int get(Item item) {
        return this.ownedItems[item.type.index][item.index];
    }

    /**
     * Sets the number of owned copies of the specified item.
     *
     * @param item The item to set the quantity of.
     * @param quantity The new quantity to set.
     */
    public void set(Item item, int quantity) {
        this.ownedItems[item.type.index][item.index] = quantity;
    }

}
