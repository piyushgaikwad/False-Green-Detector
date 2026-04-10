package com.fgd.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal regex-based JSON reader for small, well-known CI metadata files.
 *
 * Not a general-purpose JSON library. Handles the specific shapes produced by
 * the FGD ecosystem (provenance.json, cache/metadata.json). Keeping this
 * dependency-free is intentional: FGD must work as a standalone JAR in air-
 * gapped or minimal CI environments without extra classpath entries.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static String readString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    public static Boolean readBoolean(String json, String key) {
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return Boolean.parseBoolean(m.group(1).toLowerCase(Locale.ROOT));
    }

    public static Long readLong(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        try { return Long.parseLong(m.group(1)); }
        catch (NumberFormatException e) { return null; }
    }

    /** Escapes a string value for embedding in hand-built JSON. */
    public static String escape(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
