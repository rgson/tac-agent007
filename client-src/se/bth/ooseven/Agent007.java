package se.bth.ooseven;

import se.sics.tac.aw.AgentImpl;
import se.sics.tac.aw.Bid;
import se.sics.tac.aw.Quote;
import se.sics.tac.aw.TACAgent;
import se.sics.tac.util.ArgEnumerator;

import java.time.Duration;

public class Agent007 extends AgentImpl {

    // =========================================================================
    // Configuration parameters
    // =========================================================================

    /**
     * The variance threshold for the hotel tree action selection.
     * Configures the level of risk-taking behavior when selecting hotel rooms.
     */
    private static final double HOTEL_VARIANCE_THRESHOLD = Double.MAX_VALUE;

    /**
     * The number of levels the hotel tree will consider when selecting an
     * action. A higher field of vision results in a deeper tree, meaning an
     * exponential increase in dimensionality. Meanwhile, the impact of the
     * deeper levels on the actual outcome is decreasing.
     */
    private static final int HOTEL_FIELD_OF_VISION = 5;

    /**
     * The maximum amount of time allowed for the hotel tree search. Limited
     * to guarantee results in time to actually act upon the information before
     * the next auction closes.
     */
    private static final Duration HOTEL_MAX_TIME = Duration.ofSeconds(30);

    // =========================================================================
    // Agent implementation
    // =========================================================================

    /**
     * The clients' preferences.
     */
    private Preferences preferences;

    /**
     * The saved prices.
     */
    private Prices prices;

    /**
     * The owned items.
     */
    private Owns owned;

    /**
     * The probably owned items (owned items + items we're likely to win).
     */
    private Owns probablyOwned;

    /**
     * A cache of utility value calculations for various configurations of
     * owned items.
     */
    private Cache utilityCache;

    @Override
    protected void init(ArgEnumerator args) {
        this.preferences = fillPreferences();
        this.prices = new Prices();
        this.owned = new Owns();
        this.probablyOwned = new Owns();
        this.utilityCache = new Cache(this.preferences);
    }

    @Override
    public void quoteUpdated(Quote quote) {
        updatePrice(quote);
        updateOwns(quote.getAuction());
        // TODO submit updated bid if possiblyOwned < wanted.
    }

    @Override
    public void quoteUpdated(int auctionCategory) {
        // Update the stored information for every action in the category.
        for (int day = 1; day <= 5; day++) {
            for (int type = 0; type < 7; type++) {
                int auction = TACAgent.getAuctionFor(auctionCategory, type, day);
                updatePrice(agent.getQuote(auction));
                updateOwns(auction);
            }
        }
    }

    @Override
    public void auctionClosed(int auction) {
        // Set the price to MAX_VALUE as it cannot be bought.
        Item item = Item.getItemByAuctionNumber(auction);
        this.prices.set(item, Integer.MAX_VALUE);
        updateOwns(auction);
    }

    @Override
    public void bidUpdated(Bid bid) {
        int auction = bid.getAuction();
        updatePrice(agent.getQuote(auction));
    }

    @Override
    public void bidRejected(Bid bid) {
        // TODO
    }

    @Override
    public void bidError(Bid bid, int status) {
        // TODO
    }

    @Override
    public void gameStarted() {
        // TODO
    }

    @Override
    public void gameStopped() {
        this.utilityCache.stop();
    }

    /**
     * Fills a Preferences object with the client preferences.
     *
     * @return The filled Preferences object.
     */
    private Preferences fillPreferences() {
        final int CLIENTS = 8, TYPES = 6;
        int[][] prefs = new int[CLIENTS][TYPES];
        for (int client = 0; client < CLIENTS; client++) {
            for (int type = 0; type < TYPES; type++) {
                prefs[client][type] = agent.getClientPreference(client, type);
            }
        }
        return new Preferences(prefs);
    }

    /**
     * Updates the saved price using a received quote.
     *
     * @param quote The received quote.
     */
    private void updatePrice(Quote quote) {
        Item item = Item.getItemByAuctionNumber(quote.getAuction());
        if (!quote.isAuctionClosed()) {
            this.prices.set(item, (int) Math.ceil(quote.getAskPrice()));
        }
    }

    /**
     * Updates the saved information about owned and probably owned items for
     * the given auction.
     *
     * @param auction The auction number.
     */
    private void updateOwns(int auction) {
        Item item = Item.getItemByAuctionNumber(auction);

        int owned = agent.getOwn(auction);
        this.owned.set(item, owned);

        int probabilyOwned = owned + agent.getProbablyOwn(auction);
        this.probablyOwned.set(item, owned + probabilyOwned);
    }

    /**
     * Main method for backwards compatibility.
     *
     * @param args
     */
    public static void main(String[] args) {
        TACAgent.main(args);
    }

}
