package se.bth.ooseven;

import se.sics.tac.solver.FastOptimizer;

/**
 * Class representing an allocation of items to clients.
 */
public class Allocation {

    /**
     * The allocation array.
     *
     * First dimension is for each client.
     * Second dimension is for the various allocations.
     * [0-7][0] Arrival day (1-4)
     * [0-7][1] Departure day (2-5)
     * [0-7][2] Good hotel? (1 for good hotel, 0 for cheap hotel)
     * [0-7][3] Day for alligator wrestling (1-4)
     * [0-7][4] Day for amusement park (1-4)
     * [0-7][5] Day for museum (1-4)
     */
    private int[][] allocation;

    /**
     * Constructs a new Allocation object from a set of owned items and
     * preferences.
     *
     * @param owns The owned items.
     * @param preferences The client preferences.
     */
    public Allocation(Owns owns, Preferences preferences) {
        FastOptimizer optimizer = new FastOptimizer();
        optimizer.setClientData(preferences.getSolverFormat(),
                owns.getSolverFormat());
        optimizer.solve();
        this.allocation = optimizer.getLatestAllocation();
    }

    /**
     * Constructs a new Allocation object from an allocation array, following
     * the FastOptimizer's format.
     *
     * @param allocation The allocation array.
     */
    public Allocation(int[][] allocation) {
        this.allocation = ArrayUtils.copyArray(allocation);
    }

    /**
     * Checks if the specified client has a working travel package.
     *
     * @param client The client number (0 through 7).
     * @return True if the client has a working travel package, otherwise false.
     */
    public boolean hasTravelPackage(int client) {
        return this.allocation[client][0] > 0;
    }

    /**
     * Checks if the specified client has the same hotel room allocation in
     * two allocations.
     *
     * @param client The client number (0 through 7).
     * @param a1 The first allocation.
     * @param a2 The second allocation.
     * @return True if the hotel room allocation is the same for the client,
     *  otherwise false.
     */
    public static boolean hasSameRoomAllocation(int client, Allocation a1,
                                                Allocation a2) {
        // TODO not sure if the allocations will be for the same client. might have to cross-check all of them...
        return a1.getArrival(client) == a2.getArrival(client)
                && a1.getDeparture(client) == a2.getDeparture(client)
                && a1.isStayingOnGoodHotel(client) == a2.isStayingOnGoodHotel(client);
    }

    /**
     * Gets the arrival day for the client.
     *
     * @param client The client number (0 through 7).
     * @return The arrival day (1-4) or 0 if the client does not go.
     */
    public int getArrival(int client) {
        return this.allocation[client][0];
    }

    /**
     * Gets the departure day for the client.
     *
     * @param client The client number (0 through 7).
     * @return The departure day (2-5) or 0 if the client does not go.
     */
    public int getDeparture(int client) {
        return this.allocation[client][1];
    }

    /**
     * Checks if the client stays on the good hotel.
     *
     * @param client The client number (0 through 7).
     * @return True if the client is staying on the good hotel, otherwise false.
     */
    public boolean isStayingOnGoodHotel(int client) {
        return this.allocation[client][2] == 1;
    }

    /**
     * Gets the day for the client to visit the first type of entertainment.
     *
     * @param client The client number (0 through 7).
     * @return The day (1-4) or 0 if the client does not have a ticket.
     */
    public int getAlligatorDay(int client) {
        return this.allocation[client][3];
    }

    /**
     * Gets the day for the client to visit the second type of entertainment.
     *
     * @param client The client number (0 through 7).
     * @return The day (1-4) or 0 if the client does not have a ticket.
     */
    public int getAmusementDay(int client) {
        return this.allocation[client][4];
    }

    /**
     * Gets the day for the client to visit the third type of entertainment.
     *
     * @param client The client number (0 through 7).
     * @return The day (1-4) or 0 if the client does not have a ticket.
     */
    public int getMuseumDay(int client) {
        return this.allocation[client][5];
    }
    
    public int getEventDay(int client, Item.Type type) {
        int category = 3;
        switch (type) {
            case ALLIGATOR: category = 3; break;
            case AMUSEMENT: category = 4; break;
            case MUSEUM:    category = 5; break;
            default: System.err.println("Dont know about Eventtype "+type+"!");
        }
        
        return this.allocation[client][category];
    }
}
