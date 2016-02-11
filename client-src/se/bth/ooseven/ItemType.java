package se.bth.ooseven;

public enum ItemType {
    INFLIGHT(0),
    OUTFLIGHT(1),
    CHEAP_HOTEL(2),
    GOOD_HOTEL(3),
    ALLIGATOR_WRESTLING(4),
    AMUSEMENT(5),
    MUSEUM(6);

    public final int index;

    ItemType(int index) {
        this.index = index;
    }
}
