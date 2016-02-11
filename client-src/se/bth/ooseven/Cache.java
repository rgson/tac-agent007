package se.bth.ooseven;

import se.sics.tac.solver.FastOptimizer;

public class Cache {
    private final Preferences prefs;
    public Cache(Preferences prefs) {
        this.prefs = prefs;
    }
    
    public int calc(Owns owns) {
        FastOptimizer fo = new FastOptimizer();
        fo.setClientData(prefs.getSolverFormat(), owns.getSolverFormat());
    
        return fo.solve();
    }
}
