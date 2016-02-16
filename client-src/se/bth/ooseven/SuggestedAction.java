package se.bth.ooseven;


/**
 * Represents a suggested action (i.e. a bid to place).
 */
public final class SuggestedAction {

    /**
     * The suggested item to bid on.
     */
    public final Item item;

    /**
     * The maximum price for which this move is profitable.
     */
    public final int maxPrice;

    /**
     * Constructs a new SuggestedAction.
     *
     * @param item The suggested item to bid on.
     * @param maxPrice The maximum price for which this move is profitable.
     */
    public SuggestedAction(Item item, int maxPrice) {
        this.item = item;
        this.maxPrice = maxPrice;
    }

    @Override
    public String toString() {
        return String.format("(%s, %d)", this.item, this.maxPrice);
    }
}
