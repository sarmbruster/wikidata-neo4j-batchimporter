package de.thm.mni.wikidata.neo4j.mapping;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Naming {

    private Naming() {
    }

    public static String slug(String text) {
        return slugify(text, '-').toLowerCase(Locale.ROOT);
    }

    public static String relationType(String propertyLabel) {
        String type = slugify(propertyLabel, '_').toUpperCase(Locale.ROOT);
        return !type.isEmpty() && Character.isDigit(type.charAt(0)) ? "_" + type : type;
    }

    public static String claimLabel(String datatype) {
        return Arrays.stream(datatype.split("[ \\-_:]"))
                .filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining());
    }

    private static String slugify(String text, char delimiter) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        StringBuilder result = new StringBuilder();
        boolean pendingDelimiter = false;
        for (char c : ascii.toCharArray()) {
            if (isAsciiAlphanumeric(c)) {
                if (pendingDelimiter && !result.isEmpty()) {
                    result.append(delimiter);
                }
                pendingDelimiter = false;
                result.append(c);
            } else if (Character.isWhitespace(c) || c == '-' || c == '_') {
                pendingDelimiter = true;
            }
        }
        return result.toString();
    }

    private static boolean isAsciiAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
