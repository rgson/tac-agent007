package se.bth.ooseven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class EventTicketHandler {

    // CONFIGUARTION PARAMETERS

    /**
     * Never sell a ticket for a price lower than this.
     */
    private static final int MIN_SELL_PRICE = 10; // $

    /**
     * Never buy a ticket for more than this.
     */
    private static final int MAX_BUY_PRICE = 199; // $


    /**
     * Only sell a ticket if the win for that is >= value * SELL_WIN_MARGIN.
     */
    private static final double SELL_WIN_MARGIN = 0.42; // %

    /**
     * Only buy a ticket if its allocation is known to be good and we make
     * this relative amount of win of the buy.
     */
    private static final double SECURE_BUY_WIN_MARGIN = 0.33; // %

    /**
     * If we probably want to buy this ticket, but don't know yet if we will
     * need it in the end. Buy only if we make this relative amount in win.
     */
    private static final double POSSIBLE_BUY_WIN_MARGIN = 0.9; // %

    private static final double REPLACE_MARGIN = 0.9; // %
    private static final int CLIENTS = 8;
    /**
     * Item this Handler should handle.
     */
    public final Item handle;
    private final Preferences prefs;
    /**
     * Current minimum price we would want to sell one of these tickets for.
     */
    private int minSellPrice = 200;
    /**
     * Current maximum price we would want to pay for this ticket.
     */
    private int maxBuyPrice = 0;
    private int owns = 0;
    private Allocation targetAlloc = null;
    private Allocation currentAlloc = null;
    private int[] clientBonus = {0, 0, 0, 0, 0, 0, 0, 0};

    public EventTicketHandler(Preferences prefs, Item handle) {
        this.handle = handle;
        this.prefs = prefs;

        for (int i = 0; i < CLIENTS; i++) {
            clientBonus[i] = prefs.getEventBonus(i, handle.type);
        }
    }

    public void allocationUpdated(Allocation target) {
        this.targetAlloc = target;
    }

    public void ownsUpdated(Owns owns, Allocation alloc) {
        this.currentAlloc = alloc;
        this.owns = owns.get(handle);
    }

    public List<BidPoint> calculateBids() {
        if (currentAlloc == null || targetAlloc == null) {
            // TODO is this warning needed?
            System.err.println("EventTicketHandler." + handle + ": " +
                    "Can't update prices!");
            return null;
        }

        int oldMax = this.maxBuyPrice;
        int oldMin = this.minSellPrice;

        // Determine relevant clients
        List<Integer> secureBonuses = new ArrayList<>(CLIENTS);
        List<Integer> planBonuses = new ArrayList<>(CLIENTS);

        for (int i = 0; i < CLIENTS; i++) {
            if (handle.day < targetAlloc.getArrival(i)
                    || handle.day >= targetAlloc.getDeparture(i)) {
                // If the client will not be here this day, skip
                continue;
            }

            boolean feasable = true;

            // Does this client have an event this day 
            // and is that more valueble than this? -> skip
            for (Item.Type type : Item.EVENT_TYPES) {
                if (handle.type != type
                        && currentAlloc.getEventDay(i, type) == handle.day
                        && prefs.getEventBonus(i, type) >= (clientBonus[i] * (1 - REPLACE_MARGIN))
                        ) {
                    feasable = false;
                }
            }

            if (!feasable) continue;

            // This is at-least a planned client
            planBonuses.add(clientBonus[i]);

            // If this client is stable it is also secure!
            if (currentAlloc.hasTravelPackage(i)
                    && Allocation.hasSameRoomAllocation(i, currentAlloc, targetAlloc)) {
                secureBonuses.add(clientBonus[i]);
            }

        }

        // Reverse sort all Bonuses
        Collections.sort(secureBonuses);
        Collections.reverse(secureBonuses);

        Collections.sort(planBonuses);
        Collections.reverse(planBonuses);

        // Determine minimum sell price, which is the lowest gain we currently 
        // get from one of this items (+ win margin) [MIN_SELL_PRICE if we have more than we need]
        double minSellPrice = 0;
        if (owns <= 0) {
            minSellPrice = 200 * (1 + SELL_WIN_MARGIN); // 200$ Penalty
        } else if (owns > planBonuses.size()) {
            minSellPrice = 0; // We have some extra
        } else {
            minSellPrice = planBonuses.get(owns - 1) * (1 + SELL_WIN_MARGIN);
        }

        // Determine maximum buy price, which is the highest gain we get from
        // buying one ADDITIONAL of this items (- win margin)
        double maxBuyPrice = 0;
        if (owns < 0) {
            maxBuyPrice = 200 * (1 - SECURE_BUY_WIN_MARGIN); // 200$ Penalty
        } else if (owns >= secureBonuses.size()) {
            maxBuyPrice = 0; // We have some extra
        } else {
            maxBuyPrice = secureBonuses.get(owns) * (1 - SECURE_BUY_WIN_MARGIN);
        }

        // Check if our plan has a higher maxBuyPrice
        if (owns >= 0 && owns < planBonuses.size()) {
            maxBuyPrice = Math.max(planBonuses.get(owns) * (1 - POSSIBLE_BUY_WIN_MARGIN), maxBuyPrice);
        }

        if (Math.min(MAX_BUY_PRICE, (int) Math.floor(maxBuyPrice)) >= Math.max(MIN_SELL_PRICE, (int) Math.ceil(minSellPrice))) {
            System.err.println("EventTicketHandler." + handle + ": " +
                    "Want to sell for less than I want to buy, Buy: " + maxBuyPrice + ", Sell: " + minSellPrice + ", Bonuses:" + secureBonuses + planBonuses);

            minSellPrice = 240;
            maxBuyPrice = 0;
        }

        // Set prices, making sure we stay within our absolute maximums
        this.maxBuyPrice = Math.min(MAX_BUY_PRICE, (int) Math.floor(maxBuyPrice));
        this.minSellPrice = Math.max(MIN_SELL_PRICE, (int) Math.ceil(minSellPrice));

        // If the bids have changed, return the new BidPoints.
        if (this.maxBuyPrice != oldMax || this.minSellPrice != oldMin) {
            List<BidPoint> bidPoints = new ArrayList<>(2);
            bidPoints.add(new BidPoint(1, this.maxBuyPrice));
            bidPoints.add(new BidPoint(-1, this.minSellPrice));
            return bidPoints;
        }

        return null;
    }

}
