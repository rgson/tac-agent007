package se.bth.ooseven;

import java.util.Arrays;

/**
 * Represents a collection of owned items.
 *
 * Offers convenience methods for adding, setting and getting the number of
 * owned items.
 *
 * Also offers conversions between the agent's format and the solver's format.
 */
public class Owns {

    // TODO: the solver's format for input seems to differ from its internal format. Expects 5x7 array. Presumably [day][item] = count.

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
     * First dimension is day.
     * Second dimension is type.
     * [0 - 4][0] Inflights
     * [0 - 4][1] Outflights
     * [0 - 4][2] Cheap hotels
     * [0 - 4][3] Good hotels
     * [0 - 4][4] Alligator wrestling
     * [0 - 4][5] Amusement
     * [0 - 4][6] Museum
     */

    /**
     * The owned items, stored in the solver's format.
     */
    private final int[][] ownedItems;

    /**
     * Constructs a new Owns object with all quantities initialized to 0.
     */
    public Owns() {
        final int DAYS = 5;
        final int TYPES = 7;
        this.ownedItems = new int[DAYS][TYPES];
    }

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
        final int DAYS = 5;
        final int TYPES = 7;
        int[][] converted = new int[DAYS][TYPES];
        for (int day = 0; day < DAYS - 1; day++) {
            for (int type = 0; type < TYPES; type++) {
                if (type == 1) {    // Outflights are offset by 1 day.
                    converted[day + 1][type] = owns[type * 4 + day];
                } else {
                    converted[day][type] = owns[type * 4 + day];
                }
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
        this.ownedItems[item.day][item.type.index] += quantity;
    }

    /**
     * Gets the number of owned copies of the specified item.
     *
     * @param item The item to get the count for.
     */
    public int get(Item item) {
        return this.ownedItems[item.day][item.type.index];
    }

    /**
     * Sets the number of owned copies of the specified item.
     *
     * @param item The item to set the quantity of.
     * @param quantity The new quantity to set.
     */
    public void set(Item item, int quantity) {
        this.ownedItems[item.day][item.type.index] = quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Owns owns = (Owns) o;

        return Arrays.deepEquals(ownedItems, owns.ownedItems);

    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(ownedItems);
    }

    public Owns withAllFlights() {
        Owns copy = new Owns(this);
        for (Item flight : Item.FLIGHTS) {
            copy.set(flight, 8);
        }
        return copy;
    }
}
