package se.bth.ooseven;

public class Preferences {

    /*
     * The solver's format:
     * First dimension is for the different clients.
     * [0 - 7][0] Arrival day (1-4)
     * [0 - 7][1] Departure day (2-5)
     * [0 - 7][2] Hotel bonus
     * [0 - 7][3] Alligator wrestling bonus
     * [0 - 7][3] Amusement bonus
     * [0 - 7][3] Museum bonus
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
}
