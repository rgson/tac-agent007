package se.bth.ooseven;

import java.util.*;
import java.util.stream.Collectors;

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
     * Flag to signal whether pruning is used within the tree.
     * Pruning removes any branch which does not offer an increase in profit.
     */
    private final boolean prune;

    /**
     * Constructs a new HotelTree.
     *
     * @param cache The cache to use for utility calculations.
     * @param prices The prices to consider when deciding the node values.
     * @param owns The owned items at the root node.
     * @param maxDepth The maximum depth. Used for iterative deepening.
     * @param prune Flag to enable or disable pruning of the tree.
     */
    public HotelTree(Cache cache, Prices prices, Owns owns, int maxDepth,
                     boolean prune) {

        this.cache = cache;
        this.prices = new Prices(prices);
        this.prune = prune;

        // Create a copy of the owned items, filled with all available flights.
        // Lets the solver determine the utilities without considering flights.
        owns = new Owns(owns);
        for (Item flight : Item.FLIGHTS) {
            owns.set(flight, 8);
        }

        this.root = new Node(owns);
        this.root.deepen(maxDepth);
    }

    /**
     * Gets the bids suggested by the search.
     *
     * @param varianceThreshold The variance threshold. Used for determining
     *                          the amount of risk-taking behavior allowed.
     * @return A queue of suggested actions (bids), in the order they were taken
     *         during the building of the tree.
     */
    public Queue<SuggestedAction> getSuggestedActions(double varianceThreshold) {
        LinkedList<SuggestedAction> moves = new LinkedList<>();
        this.root.addSuggestedActions(moves, varianceThreshold);
        return moves;
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
        private final Owns owns;

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
        }

        /**
         * Constructs a new Node as the child of another.
         *
         * @param parent The parent node.
         * @param room The room to buy at the parent node to get to this node.
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
        }

        /**
         * Deepens the tree to the specified depth.
         *
         * @param maxDepth The maximum depth to build the tree. Used for iterative
         *                 deepening.
         */
        private void deepen(int maxDepth) {
            if (maxDepth > 0) {
                // Construct children for all allowed rooms.
                this.children = getAllowedRooms().stream()
                        .map(room -> new Node(this, room))
                        .collect(Collectors.toSet());

                // If pruning is activated, any branch not resulting in
                // immediate profit is pruned.
                if (HotelTree.this.prune) {
                    this.children = this.children.stream()
                            .filter(child -> child.value < 0)
                            .collect(Collectors.toSet());
                }

                // Continue deepening the tree.
                this.children.forEach(child -> child.deepen(maxDepth - 1));

                // If there are any children, calculate average values, etc.
                if (this.children.size() > 0) {
                    calculateStatistics();
                }
            }
        }

        /**
         * Gets allowed hotel rooms for the current node.
         *
         * The rooms are restricted such that no rooms with lower auction
         * numbers than the one that brought us to this node can be reserved.
         * This is done in order to avoid creating duplicate branches, e.g. a
         * branch for Cheap_1 -> Cheap_2 and another for Cheap_2 -> Cheap_1.
         *
         * Additionally, a maximum of eight reservations are allowed for each
         * room. While more could be useful, this drastically reduces the
         * potential size of the tree.
         *
         * @return The set of allowed hotel rooms.
         */
        private Set<Item> getAllowedRooms() {
            return Item.ROOMS.stream()
                    .filter(room -> room.flatIndex >= this.room.flatIndex)
                    .filter(room -> this.owns.get(room) < 8)                    // TODO evaluate usefulness
                    .collect(Collectors.toSet());
        }

        /**
         * Calculates statistics related to the node's children.
         *
         * The average estimated total value and the variance in estimated total
         * value of the children, as well as the estimated total value of the
         * current node is calculated.
         */
        private void calculateStatistics() {
            // Calculate the average estimated total value of all children.
            double mean = this.children.stream()
                    .mapToDouble(child -> child.estimatedTotalValue)
                    .average().getAsDouble();

            // Calculate the variance in the estimated total values.
            double variance = this.children.stream()
                    .mapToDouble(child -> child.estimatedTotalValue)
                    .reduce(0, (sum, x) -> sum + Math.pow(x - mean, 2))
                    / this.children.size();

            this.childValueAverage = mean;
            this.childValueVariance = variance;
            this.estimatedTotalValue = this.value + this.childValueAverage;
        }

        /**
         * Adds the action required to get from this node to its most
         * appropriate child to the provided queue.
         *
         * The most appropriate child is determined using the provided variance
         * threshold, which determines the amount of risk that may be taken.
         * The node with the highest estimated total value is selected from the
         * nodes within the variance threshold. A move is added only if such a
         * node exists and offers an estimated increase in profit.
         *
         * @param moves The queue of actions to which the action will be added.
         * @param varianceThreshold The variance threshold, determining the
         *                          level of risk-taking behavior.
         */
        public void addSuggestedActions(Queue<SuggestedAction> moves,
                                        double varianceThreshold) {

            // If the node has no children then there are no more actions to add.
            if (this.children == null && this.children.size() > 0) {

                // Find the most valuable child within the variance threshold.
                Node nextNode = this.children.stream()
                        .filter(child ->
                                child.childValueVariance < varianceThreshold)
                        .max((a, b) -> Double.compare(
                                        a.estimatedTotalValue,
                                        b.estimatedTotalValue))
                        .get();

                // No action is added if no next node could be selected or if
                // even the selected node represents a bad choice.
                if (nextNode != null && nextNode.estimatedTotalValue >= 0) {

                    // Add the action required to get to the next node.
                    moves.add(new SuggestedAction(nextNode.room, nextNode.value));

                    // Let the next node add more actions, recursively.
                    nextNode.addSuggestedActions(moves, varianceThreshold);
                }
            }
        }

    }

}
