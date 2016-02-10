package se.bth.ooseven;

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
        this.ownedItems = copyArray(owns);
    }

    /**
     * Constructs an Owns object as a copy of another.
     * @param owns The Owns object to copy.
     */
    public Owns(Owns owns) {
        this.ownedItems = copyArray(owns.ownedItems);
    }

    /**
     * Copies an array of integers.
     * @param original The original to be copied.
     * @return The copy.
     */
    private int[] copyArray(int[] original) {
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
    private int[][] copyArray(int[][] original) {
        int[][] copy = new int[original.length][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = new int[original[i].length];
            for (int j = 0; j < copy[i].length; j++) {
                copy[i][j] = original[i][j];
            }
        }
        return copy;
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
        return copyArray(this.ownedItems);
    }

    // ------------

    public void addInflight(int day, int quantity) {
        this.ownedItems[0][day] += quantity;
    }

    public void addOutflight(int day, int quantity) {
        this.ownedItems[0][day] += quantity;
    }

    public void addCheapHotel(int day, int quantity) {
        this.ownedItems[2][day] += quantity;
    }

    public void addGoodHotel(int day, int quantity) {
        this.ownedItems[3][day] += quantity;
    }

    public void addAlligatorWrestlingTicket(int day, int quantity) {
        this.ownedItems[4][day] += quantity;
    }

    public void addAmusementTicket(int day, int quantity) {
        this.ownedItems[5][day] += quantity;
    }

    public void addMuseumTicket(int day, int quantity) {
        this.ownedItems[6][day] += quantity;
    }
    
    public int getInflightQuantity(int day) {
        return this.ownedItems[0][day];
    }

    public int getOutflightQuantity(int day) {
        return this.ownedItems[0][day];
    }

    public int getCheapHotelQuantity(int day) {
        return this.ownedItems[2][day];
    }

    public int getGoodHotelQuantity(int day) {
        return this.ownedItems[3][day];
    }

    public int getAlligatorWrestlingTicketQuantity(int day) {
        return this.ownedItems[4][day];
    }

    public int getAmusementTicketQuantity(int day) {
        return this.ownedItems[5][day];
    }

    public int getMuseumTicketQuantity(int day) {
        return this.ownedItems[6][day];
    }

}
