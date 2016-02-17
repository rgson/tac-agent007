package se.bth.ooseven;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * A tree-structure of possible hotel room purchases.
 *
 * Uses utility values and current price information to evaluate the value of
 * various combinations of hotel room reservations.
 *
 * Given a variance threshold for calibration of the risk-taking behavior, the
 * tree produces an ordered list of actions to perform. The proposed actions
 * consist of an item type (cheap hotel/good hotel), the index (day) and the
 * maximum price that can be paid in order for the reservation to be profitable.
 *
 * Supports depth-limited construction for use with iterative deepening.
 *
 * Supports pruning of branches where no immediate profit is predicted. May miss
 * opportunities where future reservations cause the utility of previous
 * reservations to rise, but may drastically reduce the tree's size.
 */
public class HotelTree {

    /**
     * The cache used for storing/calculating utility value of owned items.
     */
    private final Cache cache;

    /**
     * The current prices, to consider when calculating the branches' utilities.
     */
    private final Prices prices;

    /**
     * The tree's root node.
     */
    private final Node root;

    /**
     * Counts the number of nodes. Used for debugging purposes only.
     */
    private final AtomicInteger nodeCount;

    /**
     * Constructs a new HotelTree.
     *
     * @param cache  The cache to use for utility calculations.
     * @param prices The prices to consider when deciding the node values.
     * @param owns   The owned items at the root node.
     */
    public HotelTree(Cache cache, Prices prices, Owns owns) {
        this.cache = cache;
        this.prices = new Prices(prices);
        this.nodeCount = new AtomicInteger(0);

        // Create a copy of the owned items, filled with all available flights.
        // Lets the solver determine the utilities without considering flights.
        owns = new Owns(owns);
        for (Item flight : Item.FLIGHTS) {
            owns.set(flight, 8);
        }

        this.root = new Node(owns);
    }

    /**
     * Gets the bids suggested by the search.
     *
     * @param varianceThreshold The variance threshold. Used for determining
     *                          the amount of risk-taking behavior allowed.
     * @param maxTime           The maximum amount of time allowed for the search.
     * @return A queue of suggested actions (bids), in the order they were taken
     * during the building of the tree.
     */
    public Queue<SuggestedAction> getSuggestedActions(double varianceThreshold,
                                                      int depthOfVision,
                                                      Duration maxTime) {

        ActionFinder finder = new ActionFinder(varianceThreshold, depthOfVision);
        Thread finderThread = new Thread(finder, "HotelTree.ActionFinder");
        try {
            // Run the finder for at most maxTime.
            finderThread.start();
            finderThread.join(maxTime.toMillis());
        } catch (InterruptedException e) {
            // Re-interrupt the current thread.
            Thread.currentThread().interrupt();
        } finally {
            // Time's up. Kindly ask the finder to terminate.
            finderThread.interrupt();
        }
        return finder.getSuggestedActions();
    }

    // =========================================================================
    // private class Node
    // =========================================================================

    /**
     * Represents a node in the tree.
     */
    private class Node {

        /**
         * The owned items, according to the scenario represented by this node.
         */
        private Owns owns;

        /**
         * The hotel room that was bought to get here from the parent node.
         */
        private final Item room;

        /**
         * The pure utility of this node.
         */
        private final int utility;

        /**
         * The value of this node, considering utility gain and cost.
         * value = utility - parent's utility - cost
         */
        private final int value;

        /**
         * The average value of the node's children.
         */
        private double childValueAverage;

        /**
         * The variance of values of the node's children.
         */
        private double childValueVariance;

        /**
         * The estimated total value of the node.
         * estimatedTotalValue = value + childValueAverage
         */
        private double estimatedTotalValue;

        /**
         * This tree node's children (i.e. all possible/allowed actions to take in
         * the current scenario.
         */
        private Set<Node> children;

        /**
         * Constructs a new Node to be used as the root node.
         *
         * @param owns The currently owned items.
         */
        public Node(Owns owns) {
            this.owns = owns;
            this.room = null;
            this.children = null;

            this.utility = HotelTree.this.cache.calc(this.owns);
            this.value = this.utility;
            this.childValueAverage = 0;
            this.childValueVariance = 0;
            this.estimatedTotalValue = this.value;

            HotelTree.this.nodeCount.incrementAndGet();
        }

        /**
         * Constructs a new Node as the child of another.
         *
         * @param parent The parent node.
         * @param room   The room to buy at the parent node to get to this node.
         */
        public Node(Node parent, Item room) {
            this.room = room;
            this.children = null;

            this.owns = new Owns(parent.owns);
            this.owns.add(this.room, 1);

            this.utility = HotelTree.this.cache.calc(this.owns);
            this.value = this.utility - parent.utility -
                    HotelTree.this.prices.get(this.room);
            this.childValueAverage = 0;
            this.childValueVariance = 0;
            this.estimatedTotalValue = this.value;

            HotelTree.this.nodeCount.incrementAndGet();
        }

        /**
         * Deepens the tree to the specified depth.
         *
         * @param maxDepth The maximum depth to build the tree. Used for iterative
         *                 deepening.
         */
        private synchronized void deepen(int maxDepth) {
            // Only deepen until the max depth is reached.
            if (maxDepth > 0) {

                // Only create children if we haven't already done so.
                if (this.children == null) {

                    // Construct children for all available rooms.
                    this.children = Item.ROOMS.stream()
                            .filter(room -> this.owns.get(room) < 16) // Only 16 copies of each rooms exist.
                            .map(room -> new Node(this, room))
                            .filter(child -> child.value >= 0)  // Better to stop than to choose a bad path.
                            .collect(Collectors.toSet());

                    // After constructing the children, we no longer need the Owns
                    // object. Release it to reclaim some memory.
                    this.owns = null;
                }

                // Continue deepening the tree.
                this.children.parallelStream()
                        .forEach(child -> child.deepen(maxDepth - 1));

                // If there are any children, calculate average values, etc.
                if (this.children.size() > 0) {
                    calculateStatistics();
                }
            }
        }

        /**
         * Calculates statistics related to the node's children.
         * <p>
         * The average estimated total value and the variance in estimated total
         * value of the children, as well as the estimated total value of the
         * current node is calculated.
         */
        private synchronized void calculateStatistics() {
            // Prepare the values of possible choices.
            double[] values = DoubleStream.concat(
                    DoubleStream.of(0), // Doing nothing is also a possibility.
                    this.children.stream()
                            .mapToDouble(child -> child.estimatedTotalValue))
                    .toArray();

            // Calculate the average estimated total value of all children.
            double mean = DoubleStream.of(values).average().getAsDouble();

            // Calculate the variance in the estimated total values.
            double variance = DoubleStream.of(values)
                    .reduce(0, (sum, x) -> sum + Math.pow(x - mean, 2))
                    / values.length;

            this.childValueAverage = mean;
            this.childValueVariance = variance;
            this.estimatedTotalValue = this.value + this.childValueAverage;
        }
    }

    // =========================================================================
    // private class ActionFinder
    // =========================================================================

    /**
     * Iteratively and selectively deepens the tree to find suggested actions.
     * The most appropriate action is determined using a heuristic approach
     * based on the given variance threshold and the estimated value of nodes.
     * <p>
     * The variance threshold determines the amount of risk that may be taken.
     * The node with the highest estimated total value is selected from the
     * nodes within the variance threshold. An action is added only if such a
     * node exists and offers an estimated increase in profit.
     */
    private class ActionFinder implements Runnable {

        /**
         * The suggested actions found.
         */
        private final Queue<SuggestedAction> actions;

        /**
         * The variance threshold specifies the level of risk-taking behavior.
         */
        private final double varianceThreshold;

        /**
         * The depth of vision specifies the depth to which the tree is grown
         * before an action is selected. This deepening is performed iteratively
         * for each selected action.
         * <p>
         * For example, a value of two would deepen the tree by two levels
         * before each action is selected.
         */
        private final int depthOfVision;

        /**
         * Constructs a new ActionFinder object.
         *
         * @param varianceThreshold The variance threshold specifies the level
         *                          of risk-taking behavior.
         * @param depthOfVision     The depth of vision specifies the depth to which
         *                          the tree is grown before an action is selected.
         */
        public ActionFinder(double varianceThreshold, int depthOfVision) {
            this.actions = new LinkedList<>();
            this.varianceThreshold = varianceThreshold;
            this.depthOfVision = depthOfVision;
        }

        /**
         * Gets the queue of suggested actions, in the order found.
         *
         * @return The suggested actions.
         */
        public Queue<SuggestedAction> getSuggestedActions() {
            return actions;
        }

        /**
         * Runs the ActionFinder.
         * Deepens the tree and puts the suggested actions in the queue.
         * Continues until the tree ends, unless interrupted.
         */
        @Override
        public void run() {
            // Start the search at the root.
            Node node = HotelTree.this.root;

            // Keep searching until interrupted.
            while (node != null && !Thread.currentThread().isInterrupted()) {

                // Deepen the tree in which this node is root.
                node.deepen(depthOfVision);

                // Extra interrupt check, as deepening might've taken a while.
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                // Find the most valuable child within the variance threshold.
                Node nextNode = node.children.stream()
                        .filter(child ->
                                child.childValueVariance < varianceThreshold
                                        && child.estimatedTotalValue >= 0)
                        .max((a, b) -> Double.compare(
                                a.estimatedTotalValue,
                                b.estimatedTotalValue))
                        .orElse(null);

                if (nextNode != null) {
                    // Save the action necessary to get to this node.
                    actions.add(new SuggestedAction(nextNode.room, nextNode.value));
                }

                // Move on to the next node.
                node = nextNode;
            }
        }
    }

    // =========================================================================
    // Entry-point for testing
    // =========================================================================

    public static void main(String[] args) {

        Owns owns = new Owns(new int[]{
                0, 0, 0, 0,
                0, 0, 0, 0,

                0, 0, 0, 0,
                0, 0, 0, 0,
//                0, 3, 4, 0,
//                0, 1, 1, 0,

                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
        });

        Prices prices = new Prices(new int[]{
                0, 0, 0, 0,
                0, 0, 0, 0,
                12, 21, 12, 21,
                123, 321, 123, 321,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
        });

        Preferences preferences = new Preferences(new int[][]{
                {1, 3, 72, 77, 78, 37},
                {2, 3, 55, 193, 180, 111},
                {1, 3, 112, 67, 149, 177},
                {1, 2, 87, 74, 72, 167},
                {2, 3, 110, 68, 193, 148},
                {1, 2, 69, 87, 142, 189},
                {1, 3, 67, 78, 154, 67},
                {1, 4, 140, 141, 3, 23},
        });

        Cache cache = new Cache(preferences);

        HotelTree tree = new HotelTree(cache, prices, owns);

        double varianceThreshold = Double.MAX_VALUE;
        int fieldOfVision = 5;
        Duration maxTime = Duration.ofSeconds(15);

        long time = System.nanoTime();
        Queue<SuggestedAction> actions = tree.getSuggestedActions(
                varianceThreshold, fieldOfVision, maxTime);
        time = System.nanoTime() - time;

        System.out.println("Time taken: " + (time / 1000000000D) + " sec.");
        System.out.println("Node count: " + tree.nodeCount);
        actions.forEach(System.out::println);

        cache.stop();
    }
}
