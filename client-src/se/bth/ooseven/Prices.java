package se.bth.ooseven;

/**
 * Represents the current prices (quotes) of individual items.
 */
public class Prices {

    /*
     * The agent's format:
     * [ 0 -  3] Inflights
     * [ 4 -  7] Outflights
     * [ 8 - 11] Cheap hotels
     * [12 - 15] Good hotels
     * [16 - 19] Alligator wrestling
     * [20 - 23] Amusement
     * [24 - 27] Museum
     */

    /**
     * The prices, stored in the agent's format.
     */
    private final int[] prices;

    /**
     * Constructs a new Prices object with all prices initialized to 0.
     */
    public Prices() {
        this.prices = new int[28];
    }

    /**
     * Constructs a new Prices object.
     *
     * @param prices An array of prices, following the agent's format.
     */
    public Prices(int[] prices) {
        this.prices = ArrayUtils.copyArray(prices);
    }

    /**
     * Constructs a new Prices object as a copy of another.
     *
     * @param prices The Prices object to copy.
     */
    public Prices(Prices prices) {
        this.prices = ArrayUtils.copyArray(prices.prices);
    }

    /**
     * Gets the current price of a specific item.
     *
     * @param item The item.
     * @return The current price of the item.
     */
    public int get(Item item) {
        return this.prices[item.flatIndex];
    }

    /**
     * Sets the current price of a specific item.
     *
     * @param item The item.
     * @param price The new price.
     */
    public void set(Item item, int price) {
        this.prices[item.flatIndex] = price;
    }

}
