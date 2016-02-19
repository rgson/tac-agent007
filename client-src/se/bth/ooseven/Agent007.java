package se.bth.ooseven;

import se.sics.tac.aw.AgentImpl;
import se.sics.tac.aw.Bid;
import se.sics.tac.aw.Quote;
import se.sics.tac.aw.TACAgent;
import se.sics.tac.solver.FastOptimizer;
import se.sics.tac.util.ArgEnumerator;

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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


    static {
        // Change the log level of the FastOptimizer's Logger to avoid spam.
        Logger log = Logger.getLogger(FastOptimizer.class.getName());
        if (log != null) {
            log.setLevel(Level.INFO);
        } else {
            System.err.println("Failed to find the FastOptimizer's Logger.");
        }
    }

    @Override
    protected void init(ArgEnumerator args) {
        System.out.println("Initializing.");
    }

    @Override
    public void quoteUpdated(Quote quote) {
        System.out.printf("Quote updated: %d\n  AskPrice: %f\n",
                quote.getAuction(), quote.getAskPrice());
        updatePrice(quote);
        updateOwns(quote.getAuction());
        // TODO submit updated bid if possiblyOwned < wanted.
    }

    @Override
    public void quoteUpdated(int auctionCategory) {
        System.out.printf("All quotes updated for %s\n",
                agent.auctionCategoryToString(auctionCategory));
    }

    @Override
    public void auctionClosed(int auction) {
        System.out.printf("Auction closed: %d\n", auction);
        // Set the price to MAX_VALUE as it cannot be bought.
        Item item = Item.getItemByAuctionNumber(auction);
        this.prices.set(item, Integer.MAX_VALUE);
        updateOwns(auction);

        placeHotelBids();
    }

    @Override
    public void bidUpdated(Bid bid) {
        System.out.printf("Bid updated: %d\n  Auction: %d\n  State: %s\n",
                bid.getID(), bid.getAuction(), bid.getProcessingStateAsString());
        int auction = bid.getAuction();
        updatePrice(agent.getQuote(auction));
    }

    @Override
    public void bidRejected(Bid bid) {
        System.out.printf("Bid rejected: %d. Reason: %s (%s)\n",
                bid.getID(), bid.getRejectReason(), bid.getRejectReasonAsString());
        // TODO
    }

    @Override
    public void bidError(Bid bid, int status) {
        System.out.printf("Bid error in auction %d: %s (%s)\n",
                bid.getAuction(), status, agent.commandStatusToString(status));
        // TODO
    }

    @Override
    public void gameStarted() {
        System.out.printf("Game %d started.\n", agent.getGameID());

        // TODO
        this.preferences = fillPreferences();
        this.prices = new Prices();
        this.owned = new Owns();
        this.probablyOwned = new Owns();
        this.utilityCache = new Cache(this.preferences);

        placeHotelBids();
    }

    @Override
    public void gameStopped() {
        System.out.println("Game stopped.");
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

    private void placeHotelBids() {
        HotelTree tree = new HotelTree(this.utilityCache, this.prices,
                this.owned);
        Queue<SuggestedAction> actions = tree.getSuggestedActions(
                HOTEL_VARIANCE_THRESHOLD, HOTEL_FIELD_OF_VISION, HOTEL_MAX_TIME);
        Map<Item, List<BidPoint>> bids = convertActionsToBids(actions);

        System.out.println("Submitting hotel bids:");
        for (Map.Entry<Item, List<BidPoint>> entry : bids.entrySet()) {
            Item item = entry.getKey();
            int auction = entry.getKey().getAuctionNumber();
            int alloc = this.owned.get(item);
            Bid bid = new Bid(auction);

            System.out.printf("Bids for item: %s\n", item);
            for (BidPoint bidPoint : entry.getValue()) {
                alloc += bidPoint.quantity;
                bid.addBidPoint(bidPoint.quantity, bidPoint.price);
                System.out.printf("  %d x %d\n",
                        bidPoint.quantity, bidPoint.price);
            }

            agent.submitBid(bid);
            agent.setAllocation(auction, alloc);
        }
    }

    private Map<Item, List<BidPoint>> convertActionsToBids(
            Queue<SuggestedAction> actions) {

        // Count the number of occurrences.
        // Item => (Price => Quantity)
        Map<Item, Map<Integer, Integer>> counts = new EnumMap<>(Item.class);
        for (SuggestedAction action : actions) {
            if (!counts.containsKey(action.item)) {
                counts.put(action.item, new HashMap<>());
            }
            counts.get(action.item)
                    .compute(action.maxPrice, (k, v) -> v == null ? 1 : v + 1);
        }
        // Replace inner Map with BidPoint list.
        Map<Item, List<BidPoint>> bids = new EnumMap<>(Item.class);
        for (Map.Entry<Item, Map<Integer, Integer>> entry : counts.entrySet()) {
            Item item = entry.getKey();
            List<BidPoint> bidPoints = entry.getValue().entrySet().stream()
                    .map(e -> new BidPoint(e.getValue(), e.getKey()))
                    .collect(Collectors.toList());
            bids.put(item, bidPoints);
        }
        return bids;
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
