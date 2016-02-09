package se.bth.ooseven;

public class Preferences {
    private final int[][] prefs;

    public Preferences(int[][] prefs) {
        this.prefs = prefs;
    }
    
    public int[][] getSolverFormat() {
        return prefs;
    }
}
