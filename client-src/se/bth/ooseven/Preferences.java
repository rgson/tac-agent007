package se.bth.ooseven;

public class Preferences {

    /*
     * The solver's format:
     * First dimension is for the different clients.
     * [0 - 7][0] Arrival day (1-4)
     * [0 - 7][1] Departure day (2-5)
     * [0 - 7][2] Hotel bonus
     * [0 - 7][3] Alligator wrestling bonus
     * [0 - 7][4] Amusement bonus
     * [0 - 7][5] Museum bonus
     */

    /**
     * The customers' preferences.
     */
    private final int[][] prefs;

    /**
     * Constructs a new Preferences object.
     *
     * @param prefs The customers' preferences in solver's format.
     */
    public Preferences(int[][] prefs) {
        this.prefs = prefs;
    }

    /**
     * Returns the preferences in the solver's format.
     * @return The preferences.
     */
    public int[][] getSolverFormat() {
        // TODO skip the copy? possibly redundant and costs memory/performance.
        return ArrayUtils.copyArray(this.prefs);
    }

    /**
     * Gets the preferred inflight for the specified client.
     *
     * @param client The client index.
     * @return The preferred inflight.
     */
    public Item getPreferredInflight(int client) {
        switch (this.prefs[client][0]) {
            case 1: return Item.INFLIGHT_1;
            case 2: return Item.INFLIGHT_2;
            case 3: return Item.INFLIGHT_3;
            case 4: return Item.INFLIGHT_4;
        }
        return null;
    }

    /**
     * Gets the preferred outflight for the specified client.
     *
     * @param client The client index.
     * @return The preferred outflight.
     */
    public Item getPreferredOutflight(int client) {
        switch (this.prefs[client][1]) {
            case 2: return Item.OUTFLIGHT_1;
            case 3: return Item.OUTFLIGHT_2;
            case 4: return Item.OUTFLIGHT_3;
            case 5: return Item.OUTFLIGHT_4;
        }
        return null;
    }

}
