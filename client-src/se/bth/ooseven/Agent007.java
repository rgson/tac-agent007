package se.bth.ooseven;

import se.sics.tac.aw.*;
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

    /**
     * The factor of how much to bid for a room, in relation to the maximum
     * amount possible before losing score by purchasing. A higher value lowers
     * the expected profit margin.
     */
    private static final float HOTEL_BID_FACTOR = 0.8f;

    /**
     * The factor of estimated price increase in hotel rooms.
     * Due to the lack of historical data, this is simply a set parameter.
     */
    private static final float HOTEL_ESTIMATED_PRICE_INCREASE = 1.25f;

    /**
     * The auto-bid price to bid on all hotel rooms that are not otherwise
     * bid on. Done on the off-chance that some rooms will be sold for free.
     */
    private static final int HOTEL_AUTOBID_PRICE = 2;

    /**
     * The number of hotel rooms to fill with auto-bid. If the number of desired
     * hotel rooms is below this number, the rest will be bought using auto-bid,
     * if they are cheap enough.
     */
    private static final int HOTEL_AUTOBID_COUNT = 8;

    /**
     * The threshold for automatic purchases of flight tickets. Any ticket
     * matching a client's preference with a price below the threshold is bought
     * until the allocation is filled.
     */
    private static final int FLIGHT_AUTOBUY_THRESHOLD = 200;


    // =========================================================================
    // Agent implementation
    // =========================================================================

    // Change the log level of the FastOptimizer's Logger to avoid spam.
    // A reference is kept to avoid garbage collection of the logger (as garbage
    // collection of the logger would reset the log level).
    private static Logger fastOptimizerLogger;
    static {
        fastOptimizerLogger = Logger.getLogger(FastOptimizer.class.getName());
        if (fastOptimizerLogger != null) {
            fastOptimizerLogger.setLevel(Level.INFO);
        } else {
            System.err.println("Failed to find the FastOptimizer's Logger.");
        }
    }

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
    /**
     * The number of closed hotel room auctions.
     */
    private int remainingHotelAuctions;
    /**
     * Flag to signal the first update of flight quotes.
     */
    private boolean firstFlightQuoteUpdate;
    
    /**
     *  Price estimators for flightprices.
     */
    private HashMap<Item,UpperBoundEstimator> priceEstimators;

    /**
     * Main method for backwards compatibility.
     *
     * @param args
     */
    public static void main(String[] args) {
        TACAgent.main(args);
    }

    @Override
    protected void init(ArgEnumerator args) {
        System.out.println("Initializing.");
    }

    @Override
    public void quoteUpdated(Quote quote) {
        System.out.printf("Quote updated: %d\n  AskPrice: $%f\n",
                quote.getAuction(), quote.getAskPrice());

        updatePrice(quote);

        switch (TACAgent.getAuctionCategory(quote.getAuction())) {
            case TACAgent.CAT_FLIGHT: flightQuoteUpdated(quote); break;
        }
    }

    @Override
    public void quoteUpdated(int auctionCategory) {
        System.out.printf("All quotes updated for %s\n",
                agent.auctionCategoryToString(auctionCategory));

        switch (auctionCategory) {
            case TACAgent.CAT_HOTEL: allHotelQuotesUpdated(); break;
            case TACAgent.CAT_FLIGHT: allFlightQuotesUpdated(); break;
        }
    }

    @Override
    public void auctionClosed(int auction) {
        System.out.printf("Auction closed: %d\n", auction);

        // Set the price to MAX_VALUE as it cannot be bought.
        Item item = Item.getItemByAuctionNumber(auction);
        this.prices.set(item, Integer.MAX_VALUE);

        if (TACAgent.getAuctionCategory(auction) == TACAgent.CAT_HOTEL) {
            this.remainingHotelAuctions--;
            System.out.printf("Hotel auctions remaining: %d\n", this.remainingHotelAuctions);
            if (this.remainingHotelAuctions == 0) {
                buyRemainingFlights();
            }
        }
    }

    @Override
    public void bidUpdated(Bid bid) {
        System.out.printf("Bid updated: %d\n  Auction: %d\n  State: %s\n",
                bid.getID(), bid.getAuction(), bid.getProcessingStateAsString());
    }

    @Override
    public void bidRejected(Bid bid) {
        System.out.printf("Bid rejected: %d. Reason: %s (%s)\n",
                bid.getID(), bid.getRejectReason(), bid.getRejectReasonAsString());
    }

    @Override
    public void bidError(Bid bid, int status) {
        System.out.printf("Bid error in auction %d: %s (%s)\n",
                bid.getAuction(), status, agent.commandStatusToString(status));
    }

    @Override
    public void transaction(Transaction transaction) {
        System.out.printf("Transaction:\n  Auction: %d\n  Quantity: %d\n  Price: $%f\n",
                transaction.getAuction(), transaction.getQuantity(), transaction.getPrice());

        updateOwns(transaction.getAuction());
    }

    @Override
    public void gameStarted() {
        System.out.printf("Game %d started.\n", agent.getGameID());

        this.preferences = fillPreferences();
        this.prices = new Prices();
        this.owned = new Owns();
        this.probablyOwned = new Owns();
        this.utilityCache = new Cache(this.preferences);
        this.remainingHotelAuctions = 8;
        this.firstFlightQuoteUpdate = true;
        this.priceEstimators = new HashMap<>();

        // NOTE: The price quotes haven't been updated yet at this point.
        // However, that doesn't matter for hotel rooms as the first quotes are
        // always 0 anyway.
        updateHotelPlan();
    }

    @Override
    public void gameStopped() {
        System.out.println("Game stopped.");

        this.utilityCache.stop();
    }

    /**
     * Called when a flight quote was updated.
     *
     * @param quote The updated quote.
     */
    private void flightQuoteUpdated(Quote quote) {
        int auction = quote.getAuction();
        Item flight = Item.getItemByAuctionNumber(auction);
        int price = (int) Math.ceil(quote.getAskPrice());
        
        if(!priceEstimators.containsKey(flight)) {
            priceEstimators.put(flight, new UpperBoundEstimator());
        }
        
        priceEstimators.get(flight).addAbsPoint(price, quote.getLastQuoteTime(), agent.getGameLength());
    }

    /**
     * Called when all the hotel room quotes have been updated.
     */
    private void allHotelQuotesUpdated() {
        updateHotelPlan();
    }

    /**
     * Called when all the flight quotes have been updated.
     */
    private void allFlightQuotesUpdated() {
        if (firstFlightQuoteUpdate) {
            firstFlightQuoteUpdate = false;
            buyFlightsBelowThreshold();
        }
    }

    /**
     * Buys flights below the configurable threshold, taking only client
     * preferences into consideration.
     */
    private void buyFlightsBelowThreshold() {
        Map<Item, Integer> counts = countFlightPreferences();
        for (Item flight : Item.FLIGHTS) {
            int price = this.prices.get(flight);
            if (price <= FLIGHT_AUTOBUY_THRESHOLD){
                int quantity = counts.get(flight) - this.owned.get(flight);
                if (quantity > 0) {
                    placeBid(flight, new BidPoint(quantity, price));
                }
            }
        }
    }

    /**
     * Calculates the preliminary flight allocation based only on the clients'
     * preferences.
     */
    private Map<Item, Integer> countFlightPreferences() {
        Map<Item, Integer> counts = new EnumMap<>(Item.class);
        final int CLIENTS = 8;
        for (int client = 0; client < CLIENTS; client++) {
            counts.compute(this.preferences.getPreferredInflight(client),
                    (k, v) -> v == null ? 1 : v + 1);
            counts.compute(this.preferences.getPreferredOutflight(client),
                    (k, v) -> v == null ? 1 : v + 1);
        }
        return counts;
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
        } else {
            // As closed auctions can no longer be bought, the price is set to the maximum amount to strongly discourage
            // such allocations.
            this.prices.set(item, Integer.MAX_VALUE);
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

        int probablyOwned = owned + agent.getProbablyOwn(auction);
        this.probablyOwned.set(item, owned + probablyOwned);
    }

    /**
     * Places a bid on an item.
     *
     * @param item     The item to bid on.
     * @param bidPoint The bid point to constitute the bid.
     */
    private void placeBid(Item item, BidPoint bidPoint) {
        System.out.printf("Placing bids for item: %s\n", item);
        Bid bid = new Bid(item.getAuctionNumber());
        System.out.printf("  %d x $%d\n",
                bidPoint.quantity, bidPoint.price);
        bid.addBidPoint(bidPoint.quantity, bidPoint.price);
        agent.submitBid(bid);
    }

    /**
     * Places a bid on an item.
     *
     * @param item      The item to bid on.
     * @param bidPoints The bid points to constitute the bid.
     */
    private void placeBid(Item item, List<BidPoint> bidPoints) {
        System.out.printf("Placing bids for item: %s\n", item);
        Bid bid = new Bid(item.getAuctionNumber());
        for (BidPoint bidPoint : bidPoints) {
            System.out.printf("  %d x $%d\n",
                    bidPoint.quantity, bidPoint.price);
            bid.addBidPoint(bidPoint.quantity, bidPoint.price);
        }
        agent.submitBid(bid);
    }

    /**
     * Updates the hotel room plan. Places updated bids for hotel rooms and buys
     * any safe flights.
     */
    private void updateHotelPlan() {
        Prices estFuturePrices = estimateFutureHotelPrices();
        HotelTree tree = new HotelTree(this.utilityCache, estFuturePrices,
                this.owned);
        HotelTree.Result result = tree.search(
                HOTEL_VARIANCE_THRESHOLD, HOTEL_FIELD_OF_VISION, HOTEL_MAX_TIME);

        placeHotelBids(result.getSuggestedActions());
        updateHotelRoomAllocations(result.getTargetOwns());
        buySafeFlights(result.getTargetOwns());
    }

    /**
     * Updates the hotel room allocations for all rooms to match the target
     * state of owned items.
     *
     * @param targetOwns The bids to be posted.
     */
    private void updateHotelRoomAllocations(Owns targetOwns) {
        for (Item room : Item.ROOMS) {
            agent.setAllocation(room.getAuctionNumber(), targetOwns.get(room));
        }
    }

    /**
     * Places hotel bids according to the suggested actions.
     *
     * @param actions The suggested actions.
     */
    private void placeHotelBids(Queue<SuggestedAction> actions) {
        Map<Item, List<BidPoint>> bids = convertSuggestionsToBids(actions);
        addMinimumHotelBids(bids);
        removeBidsBelowMinimumPrice(bids);
        addRequiredNumberOfRooms(bids);

        System.out.println("Placing hotel bids:");
        for (Map.Entry<Item, List<BidPoint>> entry : bids.entrySet()) {
            Item item = entry.getKey();
            List<BidPoint> bidPoints = entry.getValue();
            placeBid(item, bidPoints);
        }
    }

    /**
     * Adds minimum bids for all hotel rooms without more specific bids.
     *
     * @param bids The bids, soon to be submitted.
     */
    private void addMinimumHotelBids(Map<Item, List<BidPoint>> bids) {

        // Can be skipped if the minimum bid is invalid(/disabled).
        if (HOTEL_AUTOBID_PRICE <= 0) {
            return;
        }

        for (Item room : Item.ROOMS) {

            // Skip closed auctions and auctions above the fixed amount.
            Quote quote = agent.getQuote(room.getAuctionNumber());
            if (quote.isAuctionClosed() || quote.getAskPrice() >= HOTEL_AUTOBID_PRICE) {
                continue;
            }

            List<BidPoint> bidPoints = bids.getOrDefault(room, null);
            if (bidPoints == null) {
                bidPoints = new LinkedList<>();
                bids.put(room, bidPoints);
            }
            int quantity = bidPoints.stream()
                    .mapToInt(bidPoint -> bidPoint.quantity)
                    .sum();
            if (quantity < HOTEL_AUTOBID_COUNT) {
                // Add a bid point for all remaining rooms.
                bidPoints.add(new BidPoint(HOTEL_AUTOBID_COUNT - quantity, HOTEL_AUTOBID_PRICE));
            }
        }
    }

    /**
     * Filters out any bid points below the current minimum bid (ask price + 1).
     *
     * @param bids The bids (item => bid points).
     */
    private void removeBidsBelowMinimumPrice(Map<Item, List<BidPoint>> bids) {
        for (Map.Entry<Item, List<BidPoint>> entry : bids.entrySet()) {
            Item item = entry.getKey();
            List<BidPoint> bidPoints = entry.getValue();

            float minPrice = 1 + agent.getQuote(item.getAuctionNumber()).getAskPrice();
            bidPoints = bidPoints.stream()
                    .filter(bidPoint -> bidPoint.price >= minPrice)
                    .collect(Collectors.toList());
            entry.setValue(bidPoints);
        }
    }

    /**
     * Adds the required number of rooms to make the bid valid.
     *
     * From the game rules:
     *  "If the agent's current bid b' would have resulted in a purchase
     *  of q units in the current state, then the new bid b must offer to
     *  buy at least q units at ASK+1 or greater."
     *
     * @param bids
     */
    private void addRequiredNumberOfRooms(Map<Item, List<BidPoint>> bids) {
        for (Map.Entry<Item, List<BidPoint>> entry : bids.entrySet()) {
            Item item = entry.getKey();
            List<BidPoint> bidPoints = entry.getValue();

            Quote quote = agent.getQuote(item.getAuctionNumber());
            int newBidQuantity = bidPoints.stream()
                    .mapToInt(bidPoint -> bidPoint.quantity)
                    .sum();
            int missing = quote.getHQW() - newBidQuantity;
            if (missing > 0) {
                int minPrice = (int) (1 + Math.ceil(quote.getAskPrice()));
                bidPoints.add(new BidPoint(missing, minPrice));
            }
        }
    }

    /**
     * Gets an estimation of future hotel room prices based on the current
     * prices and the configurable factor of estimated price increase.
     *
     * @return The estimated future hotel room prices.
     */
    private Prices estimateFutureHotelPrices() {
        Prices estFuturePrices = new Prices(this.prices);
        for (Item room : Item.ROOMS) {
            int price = Math.max(this.prices.get(room), 1); // Assume a cost of at least $1.
            price = (int) (price * HOTEL_ESTIMATED_PRICE_INCREASE); // Estimate future price.
            estFuturePrices.set(room, price);
        }
        return estFuturePrices;
    }

    /**
     * Checks for and buys safe flights. Safe flights are flights for customers
     * whose current room allocation is the same as the target room allocation
     * (i.e. we've got all the rooms for the client).
     *
     * @param targetOwns The target state of owned items.
     */
    private void buySafeFlights(Owns targetOwns) {
        Allocation target = new Allocation(targetOwns, this.preferences);
        Allocation current = new Allocation(this.owned.withAllFlights(), this.preferences);
        Map<Item, Integer> counts = new EnumMap<>(Item.class);

        // TODO should not buy flight now if we can see that the price of that flight is decreasing

        // Find safe tickets based on stable hotel room allocations.
        final int CLIENTS = 8;
        for (int client = 0; client < CLIENTS; client++) {
            if (current.hasTravelPackage(client)
                    && Allocation.hasSameRoomAllocation(client, current, target)) {

                // Add one to the count for this inflight.
                Item inflight = Item.getInflightByDay(current.getArrival(client));
                counts.compute(inflight, (k, v) -> v == null ? 1 : v + 1);

                // Add one to the count for this outflight.
                Item outflight = Item.getOutflightByDay(current.getDeparture(client));
                counts.compute(outflight, (k, v) -> v == null ? 1 : v + 1);
            }
        }

        // Buy the safe tickets.
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            Item flight = entry.getKey();
            
            // Check if price is going down
            UpperBoundEstimator estimator = priceEstimators.get(flight);
            if(estimator.estimateChange(agent.getGameTime()+(10*1000), GAME_LENGTH) <= 0) {
                continue;
            }
            
            int quantity = entry.getValue() - this.owned.get(flight);
            if (quantity > 0) {
                // $500 buffer on the price, in case quotes are updated before the bid is registered.
                // Still only costs the actual ask price.
                int price = this.prices.get(flight) + 500;
                placeBid(flight, new BidPoint(quantity, price));
            }
        }
    }

    /**
     * Buys all missing flights after the last hotel room auction has finished.
     */
    private void buyRemainingFlights() {
        Allocation allocation = new Allocation(this.owned.withAllFlights(), this.preferences);
        Map<Item, Integer> counts = new EnumMap<>(Item.class);

        // Count the desired flights.
        final int CLIENTS = 8;
        for (int client = 0; client < CLIENTS; client++) {
            if (allocation.hasTravelPackage(client)) {

                // Add one to the count for this inflight.
                Item inflight = Item.getInflightByDay(allocation.getArrival(client));
                counts.compute(inflight, (k, v) -> v == null ? 1 : v + 1);

                // Add one to the count for this outflight.
                Item outflight = Item.getOutflightByDay(allocation.getDeparture(client));
                counts.compute(outflight, (k, v) -> v == null ? 1 : v + 1);
            }
        }

        // Buy the flights.
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            Item flight = entry.getKey();
            int quantity = entry.getValue() - this.owned.get(flight);
            if (quantity > 0) {
                // $500 buffer on the price, in case quotes are updated before the bid is registered.
                // Still only costs the actual ask price.
                int price = this.prices.get(flight) + 500;
                placeBid(flight, new BidPoint(quantity, price));
            }
        }
    }

    /**
     * Converts the suggested actions queue to BidPoints.
     *
     * @param actions The suggested action queue.
     * @return A map of (item => BidPoints).
     */
    private Map<Item, List<BidPoint>> convertSuggestionsToBids(
            Queue<SuggestedAction> actions) {

        // Count the number of occurrences.
        // Item => (Price => Quantity)
        Map<Item, Map<Integer, Integer>> counts = new EnumMap<>(Item.class);
        for (SuggestedAction action : actions) {
            if (!counts.containsKey(action.item)) {
                counts.put(action.item, new HashMap<>());
            }

            int price = (int) Math.ceil(action.maxPrice * HOTEL_BID_FACTOR);
            counts.get(action.item)
                    .compute(price, (k, v) -> v == null ? 1 : v + 1);
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

}
