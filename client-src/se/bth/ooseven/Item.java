package se.bth.ooseven;

import java.util.EnumSet;
import java.util.Set;

/**
 * An enum representing all items in the game.
 */
public enum Item {

    INFLIGHT_1    (Type.INFLIGHT,    0),
    INFLIGHT_2    (Type.INFLIGHT,    1),
    INFLIGHT_3    (Type.INFLIGHT,    2),
    INFLIGHT_4    (Type.INFLIGHT,    3),
    OUTFLIGHT_1   (Type.OUTFLIGHT,   1),
    OUTFLIGHT_2   (Type.OUTFLIGHT,   2),
    OUTFLIGHT_3   (Type.OUTFLIGHT,   3),
    OUTFLIGHT_4   (Type.OUTFLIGHT,   4),
    CHEAP_HOTEL_1 (Type.CHEAP_HOTEL, 0),
    CHEAP_HOTEL_2 (Type.CHEAP_HOTEL, 1),
    CHEAP_HOTEL_3 (Type.CHEAP_HOTEL, 2),
    CHEAP_HOTEL_4 (Type.CHEAP_HOTEL, 3),
    GOOD_HOTEL_1  (Type.GOOD_HOTEL,  0),
    GOOD_HOTEL_2  (Type.GOOD_HOTEL,  1),
    GOOD_HOTEL_3  (Type.GOOD_HOTEL,  2),
    GOOD_HOTEL_4  (Type.GOOD_HOTEL,  3),
    ALLIGATOR_1   (Type.ALLIGATOR,   0),
    ALLIGATOR_2   (Type.ALLIGATOR,   1),
    ALLIGATOR_3   (Type.ALLIGATOR,   2),
    ALLIGATOR_4   (Type.ALLIGATOR,   3),
    AMUSEMENT_1   (Type.AMUSEMENT,   0),
    AMUSEMENT_2   (Type.AMUSEMENT,   1),
    AMUSEMENT_3   (Type.AMUSEMENT,   2),
    AMUSEMENT_4   (Type.AMUSEMENT,   3),
    MUSEUM_1      (Type.MUSEUM,      0),
    MUSEUM_2      (Type.MUSEUM,      1),
    MUSEUM_3      (Type.MUSEUM,      2),
    MUSEUM_4      (Type.MUSEUM,      3);

    /**
     * The item type.
     */
    public final Type type;

    /**
     * The item's day.
     */
    public final int day;

    /**
     * The sequential index of the item.
     * Matches the index in the agent's format.
     */
    public final int flatIndex;

    /**
     * Constructs a new Item.
     *
     * @param type The item's type.
     * @param day The item's day.
     */
    Item(Type type, int day) {
        this.type = type;
        this.day = day;

        if (this.type == Type.OUTFLIGHT) {
            this.flatIndex = type.index * 4 + day - 1;
        } else {
            this.flatIndex = type.index * 4 + day;
        }
    }

    /**
     * Returns the item's auction number.
     *
     * @return The auction number.
     */
    public int getAuctionNumber() {
        return this.ordinal();
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

    public static Item getItemByAuctionNumber(int auctionNumber) {
        return Item.values()[auctionNumber];
    }

    public static Item getInflightByDay(int day) {
        switch (day) {
            case 1: return INFLIGHT_1;
            case 2: return INFLIGHT_2;
            case 3: return INFLIGHT_3;
            case 4: return INFLIGHT_4;
            default:
                System.err.printf("Invalid day for inflight: %d\n", day);
                return null;
        }
    }

    public static Item getOutflightByDay(int day) {
        switch (day) {
            case 2: return OUTFLIGHT_1;
            case 3: return OUTFLIGHT_2;
            case 4: return OUTFLIGHT_3;
            case 5: return OUTFLIGHT_4;
            default:
                System.err.printf("Invalid day for outflight: %d\n", day);
                return null;
        }
    }

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
        ALLIGATOR(4),
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
