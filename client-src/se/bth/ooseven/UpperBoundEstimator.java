package se.bth.ooseven;

class UpperBoundEstimator {
    
    public UpperBoundEstimator() {
    
    }
    
    public static class Interval {
        public final int min;
        public final int max;
        
        public Interval(int min, int max) {
            this.min = min;
            this.max = max;
        }
        
        public String toString() {
            return "["+min+","+max+"]";
        }
    }
    
    /**
     *  If-Else Statements from se.sics.tac.server.classic.OnesideContinuousAuction2, line 169ff
     *  Idea: xt==0 should be very rare because it is double
     */
    public static Interval getInterval(double xt) {
        int xti = (int) Math.round(xt);
        
        if (xt < 0.0) {
            return new Interval(xti, 10);
        } else if (xt > 0.0) {
            return new Interval(-10, xti);
        } else {
            return new Interval(-10, 10);
        }
    }
    
    /**
     *  Calculates possible values for x(t) from a given Delta.
     */
    public static Interval getPossibleRange(int delta) {
        int min = -10;
        int max = 30;
        
        if(delta < -10) { // xt > 0
            min = 0;
        }
        
        if(delta > 10) { // xt < 0
            max = 0;
        }
        
        return new Interval(min, max);
    }
    
    public static Set<Double> reverseIfElse(int delta) {
        // Consindering only two cases sice xt==0 should be rare
        
    }
    
    /**
     *  x(t)-function from the Game description, directly taken from the
     *  server-code (se.sics.tac.server.classic.OnesideContinuousAuction2, line 168)
     *
     *  @param upperBound An value in [-10, 30]
     *  @param timeInGame Time since the start of this game in milliseconds
     *  @param gameLength Length of the game in milliseconds
     *  @returns x(t) according to the game description [10, 30]
     */
    public static double x(int upperBound, long timeInGame, int gameLength) {
        return 10 + (((double) timeInGame / gameLength) * (upperBound - 10));
    }
    
    /**
     *  Reverse function of x(t), solved with WolframAlpha
     *  @param xt Value to be reversed
     *  @param timeInGame Time since the start of this game in milliseconds
     *  @param gameLength Length of the game in milliseconds
     *  @returns The upperBound used in the x(t) calculation
     */
    public static double reverseX(double xt, long timeInGame, int gameLength) {
        return (gameLength*(xt-10) + (double) 10*timeInGame) / timeInGame;
    }
    
    /**
     *  Main Function for testing of this class.
     */
    public static void main(String[] argv) {
        /** Length of this game in milliseconds */  // See se.sics.tac.server.Game, line 38
        int  gameLength = 9*60 * 1000;              // See se.sics.tac.server.classic.ClassicMarket, line 46
        long timeInGame = gameLength/2;               // Testvalue
        int  upperBound = 30;
        
        for(int i=-10; i<=30; i++) {
            System.out.printf("%d; %f\n", i, x(i,1,2));
        }
    }
}


