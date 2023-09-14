package me.enderkill98.ringmessage.util;

import java.util.Collection;
import java.util.stream.Stream;

public class StringUtil {

    private static final String ALLOWED_USERNAME_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toLowerCase() + "0123456789_";
    public static boolean isPossibleUsername(String maybeUsername) {
        if(maybeUsername.length() < 1 || maybeUsername.length() > 16)
            return false;
        for(char c : maybeUsername.toCharArray()) {
            if(ALLOWED_USERNAME_CHARS.indexOf(c) == -1)
                return false;
        }
        return true;
    }

    public static String join(String separator, String lastSeparator, String... elements) {
        StringBuilder list = new StringBuilder();
        for(int i = 0; i < elements.length; i++) {
            if(i > 0) {
                if(i != elements.length -1) list.append(separator);
                else list.append(lastSeparator);
            }
            list.append(elements[i]);
        }
        return list.toString();
    }

    public static String join(String separator, String lastSeparator, Collection<String> elements) {
        return join(separator, lastSeparator, elements.toArray(new String[0]));
    }

    public static String join(String separator, String lastSeparator, Stream<?> elements) {
        return join(separator, lastSeparator, elements.map(Object::toString).toList());
    }

    public static String joinCommaSeparated(String... elements) {
        return join(", ", ", ", elements);
    }

    public static String joinCommaSeparated(Collection<String> elements) {
        return join(", ", ", ", elements);
    }

    public static String joinCommaSeparated(Stream<?> elements) {
        return join(", ", ", ", elements);
    }

    public static String joinCommaPlusAndSeparated(String... elements) {
        return join(", ", " and ", elements);
    }

    public static String joinCommaPlusAndSeparated(Collection<String> elements) {
        return join(", ", " and ", elements);
    }

    public static String joinCommaPlusAndSeparated(Stream<?> elements) {
        return join(", ", " and ", elements);
    }

}
