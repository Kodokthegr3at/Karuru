package util;

import javax.servlet.http.HttpServletRequest;

/** Typed access to request parameters. */
public final class Params {

    private Params() {
    }

    /** The parameter as an int, or null when it is missing or not a number. */
    public static Integer optInt(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The parameter as a long, or null when it is missing or not a number. */
    public static Long optLong(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The parameter trimmed, or null when missing or blank. */
    public static String opt(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
