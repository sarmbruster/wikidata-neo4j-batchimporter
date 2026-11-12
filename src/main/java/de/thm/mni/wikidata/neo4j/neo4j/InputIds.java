package de.thm.mni.wikidata.neo4j.neo4j;

/**
 * Encodes node keys as non-negative longs for {@code IdType.INTEGER}: {@code Q42} and {@code P31} for entities,
 * {@code Q42#7} for the claim node of the statement at position 7 of entity Q42.
 *
 * <p>Neo4j accepts integer ids up to 2^56 - 1. Layout: bits 55..54 hold the kind (1 item, 2 property, 3 claim).
 * Entities keep their number in bits 53..0. Claims store the owner kind in bit 53, the owner number in bits 52..20
 * and the statement position in bits 19..0.
 */
final class InputIds {

    static final long MAX_ID = (1L << 56) - 1;
    private static final int KIND_SHIFT = 54;
    private static final long ITEM = 1;
    private static final long PROPERTY = 2;
    private static final long CLAIM = 3;
    private static final int OWNER_KIND_SHIFT = 53;
    private static final int OWNER_SHIFT = 20;
    private static final long MAX_ENTITY_NUMBER = (1L << KIND_SHIFT) - 1;
    private static final long MAX_OWNER_NUMBER = (1L << (OWNER_KIND_SHIFT - OWNER_SHIFT)) - 1;
    private static final long MAX_STATEMENT_INDEX = (1L << OWNER_SHIFT) - 1;

    private InputIds() {
    }

    static long encode(String key) {
        if (key.length() < 2) {
            throw invalid(key);
        }
        boolean item = switch (key.charAt(0)) {
            case 'Q' -> true;
            case 'P' -> false;
            default -> throw invalid(key);
        };
        int separator = key.indexOf('#');
        int numberEnd = separator < 0 ? key.length() : separator;
        long number = parseNumber(key, 1, numberEnd);
        if (separator < 0) {
            if (number > MAX_ENTITY_NUMBER) {
                throw invalid(key);
            }
            return ((item ? ITEM : PROPERTY) << KIND_SHIFT) | number;
        }
        long index = parseNumber(key, separator + 1, key.length());
        if (number > MAX_OWNER_NUMBER || index > MAX_STATEMENT_INDEX) {
            throw invalid(key);
        }
        long ownerKind = item ? 0 : 1;
        return (CLAIM << KIND_SHIFT) | (ownerKind << OWNER_KIND_SHIFT) | (number << OWNER_SHIFT) | index;
    }

    static String describe(long id) {
        long kind = id >>> KIND_SHIFT;
        if (kind == ITEM) {
            return "Q" + (id & MAX_ENTITY_NUMBER);
        }
        if (kind == PROPERTY) {
            return "P" + (id & MAX_ENTITY_NUMBER);
        }
        if (kind == CLAIM) {
            String owner = ((id >>> OWNER_KIND_SHIFT) & 1) == 0 ? "Q" : "P";
            return owner + ((id >>> OWNER_SHIFT) & MAX_OWNER_NUMBER) + "#" + (id & MAX_STATEMENT_INDEX);
        }
        return Long.toString(id);
    }

    private static long parseNumber(String key, int from, int to) {
        if (from >= to || to - from > 18) {
            throw invalid(key);
        }
        long number = 0;
        for (int i = from; i < to; i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                throw invalid(key);
            }
            number = number * 10 + (c - '0');
        }
        return number;
    }

    private static IllegalArgumentException invalid(String key) {
        return new IllegalArgumentException("Cannot encode node key '" + key + "'");
    }
}
