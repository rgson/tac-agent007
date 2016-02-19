package se.bth.ooseven;

import se.sics.tac.solver.FastOptimizer;

public class Allocation {

    private int[][] allocation;

    public Allocation(Owns owns, Preferences preferences) {
        FastOptimizer optimizer = new FastOptimizer();
        optimizer.setClientData(preferences.getSolverFormat(),
                owns.getSolverFormat());
        optimizer.solve();
        this.allocation = optimizer.getLatestAllocation();
    }

    public Allocation(int[][] allocation) {
        this.allocation = ArrayUtils.copyArray(allocation);
    }

    public static boolean hasSameRoomAllocation(int client, Allocation a1,
                                                Allocation a2) {
        return a1.getArrival(client) == a2.getArrival(client)
                && a1.getDeparture(client) == a2.getDeparture(client)
                && a1.isStayingOnGoodHotel(client) == a2.isStayingOnGoodHotel(client);
    }

    public int getArrival(int client) {
        return this.allocation[client][0];
    }

    public int getDeparture(int client) {
        return this.allocation[client][1];
    }

    public boolean isStayingOnGoodHotel(int client) {
        return this.allocation[client][2] == 1;
    }

    public int getAlligatorDay(int client) {
        return this.allocation[client][3];
    }

    public int getAmusementDay(int client) {
        return this.allocation[client][4];
    }

    public int getMuseumDay(int client) {
        return this.allocation[client][5];
    }
}
