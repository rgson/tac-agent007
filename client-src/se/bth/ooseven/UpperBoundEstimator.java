package se.bth.ooseven;

import java.util.stream.*;
import java.util.*;

/**
 *  This class tries to estimate the hidden variable used by the server to
 *  generate the price changes to flights.
 *
 *  To achieve this a curve-fittig algorithm is used.
 */

class UpperBoundEstimator {
    private final Set<Curve> curves;
    private Integer lastPoint = null;
    private int datapoints = 0;

    /**
     *  Sets up the set of all possible curves that are possible in the game.
     */
    public UpperBoundEstimator() {
        this.curves = IntStream.range(-10, 30 + 1)
                        .mapToObj(i -> new Curve(i))
                        .collect(Collectors.toSet());
    }
    
    /**
     *  Adds a absolute point, keeping track of the relative price changes
     *  internaly.
     */        
    public void addAbsPoint(int price, long timeInGame, int gameLength) {
        if(lastPoint == null) {
            lastPoint = price;
        } else {
            addPoint(price - lastPoint, timeInGame, gameLength);
            lastPoint = price;
        }
    }
    
    /**
     *  Add a new datapoint to the knowledgebase of this estimator.
     * 
     *  The values are directly passed to all curves
     */
    public void addPoint(int delta, long timeInGame, int gameLength) {
        curves.stream().forEach(c -> c.addPoint(delta, timeInGame, gameLength));
        datapoints++;
    }
    
    /**
     *  Generate a Stream of Curves witch never had a datapoint outside their
     *  possible range and have an error of less than the average error.
     */
    public Stream<Curve> getPossibleCurves() {
        //double avgError = getAvgError();
        return curves.stream()
                .filter(c -> c.possible);
                //.filter(c -> c.totalError <= avgError);
    }
    
    /**
     *  Returns the average error of all the possible curves.
     */
    /*public double getAvgError() {
        return curves.stream()
                .filter(c -> c.possible)
                .mapToDouble(c -> c.totalError)
                .average()
                .getAsDouble();
    }*/
    
    /**
     *  Function for predicting a price change for a given Time and GameLength.
     */
    public int estimateChange(long timeInGame, int gameLength) {
        if(datapoints == 0) {
            System.err.println("Estimator: Was asked to estimate without having datapoints!");
        }
    
        double est = 0.0;
        
        List<Curve> possible = getPossibleCurves().collect(Collectors.toList());
        Collections.sort(possible);
        Collections.reverse(possible);
        
        for(Curve c : possible) {
            est += getInterval(c.upperBound, timeInGame, gameLength).avg();
            est /= 2;
        }
        
        return (int) Math.round(est);
    }
    
    /**
     *  One possible curve, holding the upperBound and the error for all 
     *  datapoints to this curve.
     */
    public class Curve implements Comparable<Curve> {
        public final int upperBound;
        public boolean possible  = true;
        public double totalError = 0.0;
        
        public Curve(int upperBound) {
            this.upperBound = upperBound;
        }
        
        /**
         *  Adds a datapoint to this curve. Changes the error and the checks
         *  if this curve is still possible.
         */
        public void addPoint(int delta, long timeInGame, int gameLength) {
            Interval interval = getInterval(upperBound, timeInGame, gameLength);
            
            possible &= interval.containins(delta);
            
            // Sum of smallest squares
            totalError += Math.pow(((double) delta - interval.avg()), 2);
        }
        
        /**
         *  Compares the error of this curve to another one. Used for sorting.
         */
        public int compareTo(Curve other) {
            if(this.possible && !other.possible) {
                return -1;
            }
            
            if(!this.possible && other.possible) {
                return 1;
            }
            
            return Double.compare(this.totalError, other.totalError);
        }
        
        /**
         *  Generate a String representation to this curve.
         */
        public String toString() {
            return ""+upperBound+" (e="+totalError+")";
        }
    }
    
    /**
     *  Helper class for representing an Interval (min to max).
     */
    public static class Interval {
        public final int min;
        public final int max;
        
        public Interval(int min, int max) {
            this.min = min;
            this.max = max;
        }
        
        /**
         *  Average value of the Interval.
         */
        public double avg() {
            return (double) (max+min)/2;
        }
        
        /**
         *  Check if a value lies within this Interval.
         */
        public boolean containins(int val) {
            return (min <= val) && (val <= max);
        }
        
        /**
         *  Pick one random value from this Interval.
         */
        public int random() {
            Random rand = new Random();
            return min + rand.nextInt((max-min)+1);
        }
        
        /**
         *  String representation of this interval. Example: [23,42]
         */
        public String toString() {
            return "["+min+","+max+"]";
        }
    }
    
    /**
     *  Get possible value according to upperBound and time
     */
    public static Interval getInterval(int upperBound, long timeInGame, int gameLength) {
        return getInterval(x(upperBound, timeInGame, gameLength));
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
}


