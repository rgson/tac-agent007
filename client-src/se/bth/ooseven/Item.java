package se.bth.ooseven;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enum representing all items in the game.
 */
public enum Item {

    INFLIGHT_1 (Type.INFLIGHT, 1),
    INFLIGHT_2 (Type.INFLIGHT, 2),
    INFLIGHT_3 (Type.INFLIGHT, 3),
    INFLIGHT_4 (Type.INFLIGHT, 4),
    OUTFLIGHT_1 (Type.OUTFLIGHT, 1),
    OUTFLIGHT_2 (Type.OUTFLIGHT, 2),
    OUTFLIGHT_3 (Type.OUTFLIGHT, 3),
    OUTFLIGHT_4 (Type.OUTFLIGHT, 4),
    CHEAP_HOTEL_1 (Type.CHEAP_HOTEL, 1),
    CHEAP_HOTEL_2 (Type.CHEAP_HOTEL, 2),
    CHEAP_HOTEL_3 (Type.CHEAP_HOTEL, 3),
    CHEAP_HOTEL_4 (Type.CHEAP_HOTEL, 4),
    GOOD_HOTEL_1 (Type.GOOD_HOTEL, 1),
    GOOD_HOTEL_2 (Type.GOOD_HOTEL, 2),
    GOOD_HOTEL_3 (Type.GOOD_HOTEL, 3),
    GOOD_HOTEL_4 (Type.GOOD_HOTEL, 4),
    ALLIGATOR_WRESTLING_1 (Type.ALLIGATOR_WRESTLING, 1),
    ALLIGATOR_WRESTLING_2 (Type.ALLIGATOR_WRESTLING, 2),
    ALLIGATOR_WRESTLING_3 (Type.ALLIGATOR_WRESTLING, 3),
    ALLIGATOR_WRESTLING_4 (Type.ALLIGATOR_WRESTLING, 4),
    AMUSEMENT_1 (Type.AMUSEMENT, 1),
    AMUSEMENT_2 (Type.AMUSEMENT, 2),
    AMUSEMENT_3 (Type.AMUSEMENT, 3),
    AMUSEMENT_4 (Type.AMUSEMENT, 4),
    MUSEUM_1 (Type.MUSEUM, 1),
    MUSEUM_2 (Type.MUSEUM, 2),
    MUSEUM_3 (Type.MUSEUM, 3),
    MUSEUM_4 (Type.MUSEUM, 4);

    /**
     * The item type.
     */
    public final Type type;

    /**
     * The item's index (within its type).
     */
    public final int index;

    /**
     * The sequential index of the item.
     * Matches the index in the agent's format.
     */
    public final int flatIndex;

    /**
     * Constructs a new Item.
     *
     * @param type The item's type.
     * @param index The item's index (within its type).
     */
    Item(Type type, int index) {
        this.type = type;
        this.index = index;
        this.flatIndex = type.index * 4 + index;
    }

    /**
     * A set of all hotel rooms.
     */
    public static final Set<Item> ROOMS = EnumSet.of(
            CHEAP_HOTEL_1, CHEAP_HOTEL_2, CHEAP_HOTEL_3, CHEAP_HOTEL_4,
            GOOD_HOTEL_1, GOOD_HOTEL_2, GOOD_HOTEL_3, GOOD_HOTEL_4
    );

    /**
     * A set of all flights.
     */
    public static final Set<Item> FLIGHTS = EnumSet.of(
            INFLIGHT_1, INFLIGHT_2, INFLIGHT_3, INFLIGHT_4,
            OUTFLIGHT_1, OUTFLIGHT_2, OUTFLIGHT_3, OUTFLIGHT_4
    );

    // =========================================================================
    // public enum Type
    // =========================================================================

    /**
     * The item types in the game.
     */
    public enum Type {
        INFLIGHT(0),
        OUTFLIGHT(1),
        CHEAP_HOTEL(2),
        GOOD_HOTEL(3),
        ALLIGATOR_WRESTLING(4),
        AMUSEMENT(5),
        MUSEUM(6);

        /**
         * The item type's index in the solver's format.
         * Also matches the general order of items in the agent's format.
         */
        public final int index;

        /**
         * Constructs a new ItemType.
         *
         * @param index The index of the item type.
         */
        Type(int index) {
            this.index = index;
        }
    }

}
