package se.bth.ooseven;

import se.sics.tac.aw.*;
import java.util.*;
import java.util.concurrent.*;

class EventTicketHandler {

    // CONFIGUARTION PARAMETERS

    /**
     *  Never sell a ticket for a price lower than this.
     */
    private static final int MIN_SELL_PRICE = 0; // $
    
    /**
     *  Never buy a ticket for more than this.
     */
    private static final int MAX_BUY_PRICE  = 199; // $
    
    
    /**
     *  Only sell a ticket if the win for that is >= value * SELL_WIN_MARGIN.
     */
    private static final double SELL_WIN_MARGIN = 0.2; // %
    
    /**
     *  Only buy a ticket if its allocation is known to be good and we make
     *  this relative amount of win of the buy.
     */
    private static final double SECURE_BUY_WIN_MARGIN = 0.25; // %
    
    /**
     *  If we probably want to buy this ticket, but don't know yet if we will
     *  need it in the end. Buy only if we make this relative amount in win.
     */
    private static final double POSSIBLE_BUY_WIN_MARGIN = 0.5; // %
    
    
    /**
     *  If any ticket is below this price, buy it immediatly. Until the
     *  END_BUY_CHEAP time is reached.
     */
    private static final int CHEAP_PRICE = 25; // $
    
    /**
     *  Only buy cheap tickets until this time is reached.
     */
    private static final int END_BUY_CHEAP = 4*60*1000; // ms from start; 4 min

    /**
     *  Flag for the BidManager to stop/run.
     */
    private boolean run = true;

    /**
     *  Current minimum price we would want to sell one of these tickets for.
     */
    private Integer minSellPrice = 1337;
    
    /**
     *  Current maximum price we would want to pay for this ticket.
     */
    private Integer maxBuyPrice  = -1;
    
    
    /**
     *  Agent used for bidding.
     */
    private final TACAgent agent;
    
    /**
     *  Item this Handler should handle.
     */
    private final Item handle;
    
    /**
     *  Background Thread for managing the auctions.
     */
    private final Thread bidManager;
    
    /**
     *  Flag for signaling the Manager that it needs to run.
     */
    private final Semaphore runManager = new Semaphore(1);
    
    private final Preferences prefs;
    
    private Quote quote = null;
    private int owns = 0;
    private Allocation targetAlloc = null;
    private Allocation currentAlloc = null;
    
    private int[] clientBonus = {0, 0, 0, 0, 0, 0, 0, 0};
    private static final int CLIENTS = 8;
    
    
    int dutchValue = 200;
    int englishValue = 0;
    
    public EventTicketHandler(TACAgent agent, Preferences prefs, Item handle) {
        this.agent = agent;
        this.handle = handle;
        this.prefs = prefs;
        
        for(int i=0; i < CLIENTS; i++) {
            clientBonus[i] = prefs.getEventBonus(i, handle.type);
        }
        
        bidManager = new Thread(new BidManager(), "BidManager."+handle);
    }
    
    public void stop() {
        run = false;
        bidManager.interrupt();
    }
    
    public void allocationUpdated(Allocation target) {
        this.targetAlloc = target;
        
        updatePrices();
        
        runManager.release();
    }
    
    public void ownsUpdated(Owns owns, Allocation alloc) {
        this.currentAlloc = alloc;
        this.owns = owns.get(handle);
        
        updatePrices();
    
        runManager.release();
    }
    
    public void quoteUpdated(Quote quote) {
        Item qItem = Item.getItemByAuctionNumber(quote.getAuction());
        if(!qItem.equals(handle)) {
            System.err.println("EventTicketHandler."+handle+": "+
                "Got an update for "+qItem+" this should not happen!");
            return;
        }
        
        this.quote = quote;    
        runManager.release();
    }
    
    private void updatePrices() {
        if(currentAlloc == null || targetAlloc == null) {
            // TODO is this warning needed?
            System.err.println("EventTicketHandler."+handle+": "+
                "Can't update prices!");
            return;
        }
        
        // Determine relevant clients
        List<Integer> secureBonuses = new ArrayList<>(CLIENTS);
        List<Integer> planBonuses = new ArrayList<>(CLIENTS);
        
        for(int i=0; i < CLIENTS; i++) {
            if(handle.day < targetAlloc.getArrival(i) 
                ||  handle.day > targetAlloc.getDeparture(i)) {
                // If the client will not be here this day, skip
                continue;
            }
            
            // Does this client have an event this day 
            // and is that more valueble than this? -> skip
            for(Item.Type type : Item.EVENT_TYPES) {
                if(handle.type != type
                    && currentAlloc.getEventDay(i, type) == handle.day
                    && prefs.getEventBonus(i, type) > clientBonus[i]) {
                    continue;
                }
            }
            
            // This is at-least a planned client
            planBonuses.add(clientBonus[i]);
            
            // If this client is stable it is also secure!
            if(currentAlloc.hasTravelPackage(i)
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
        if(owns <= 0) {
            minSellPrice = 200 * (1+SELL_WIN_MARGIN); // 200$ Penalty
        } else if (owns > secureBonuses.size()) {
            minSellPrice = 0; // We have some extra
        } else {
            minSellPrice = secureBonuses.get(owns-1) * (1+SELL_WIN_MARGIN);
        }
        
        // Determine maximum buy price, which is the highest gain we get from
        // buying one ADDITIONAL of this items (- win margin)
        double maxBuyPrice  = 0;
        if(owns < 0) {
            maxBuyPrice = 200 * (1-SECURE_BUY_WIN_MARGIN); // 200$ Penalty
        } else if (owns >= secureBonuses.size()) {
            maxBuyPrice = 0; // We have some extra
        } else {
            maxBuyPrice = secureBonuses.get(owns) * (1-SECURE_BUY_WIN_MARGIN);
        }
        
        // Check if our plan has a higher maxBuyPrice
        if (owns >= 0 && owns < planBonuses.size()) {
            maxBuyPrice = Math.max(planBonuses.get(owns) * (1-POSSIBLE_BUY_WIN_MARGIN), maxBuyPrice);
        }
        
        // Set prices, making sure we stay within our absolute maximums
        this.maxBuyPrice  = Math.min(MAX_BUY_PRICE,  (int) Math.floor(maxBuyPrice));
        this.minSellPrice = Math.max(MIN_SELL_PRICE, (int) Math.ceil(minSellPrice));
    }
    
    private class BidManager implements Runnable {
        public void run() {
            boolean called = false;
            
            long lastDutchUpdate = 0;
            while(run) {
                try {
                    called = runManager.tryAcquire(1, TimeUnit.SECONDS);

                    // run dutch auction for selling (one at a time)
                    if(!called) {
                        // Go slowely down till the end of the game
                        dutchValue -= (minSellPrice - dutchValue) / ((agent.getGameTimeLeft()/1000) + 1);
                        lastDutchUpdate = agent.getGameTime();
                    }
                    
                    if(dutchValue < minSellPrice) {
                        dutchValue = minSellPrice;
                    }
                    
                    // TODO remove...
                    dutchValue = minSellPrice;
                    
                    
                    // TODO run english auction for buying (one at a time)
                    englishValue = maxBuyPrice;
                    
                    
                    // TODO Check other peoples prices (?)
                    
                    // place bids
                    Bid bid = new Bid(handle.getAuctionNumber());
                    bid.addBidPoint(1, englishValue);
                    bid.addBidPoint(-1, dutchValue);
                    
                    agent.submitBid(bid);
                } catch (InterruptedException ex) {
                    System.err.println("BidManager."+handle+" got interrupted!");
                }
            }
        }
    }
}
