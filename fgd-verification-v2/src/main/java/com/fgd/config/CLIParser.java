package com.fgd.config;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses raw CLI arguments into a validated {@link FGDConfig}.
 *
 * Accepts "--key value" pairs. Boolean flags (no following value) default to
 * "true". Unknown flags are silently ignored to allow forward-compatibility.
 */
public final class CLIParser {

    private CLIParser() {}

    public static FGDConfig parse(String[] args) {
        Map<String, String> raw = tokenize(args);

        FGDConfig.Builder b = FGDConfig.builder()
                .commitId(require(raw, "--commit"))
                .jobExitCode(integer(raw, "--exit-code", 0));

        if (raw.containsKey("--artifacts-dir"))    b.artifactsDir(raw.get("--artifacts-dir"));
        if (raw.containsKey("--logs"))             b.logsPath(raw.get("--logs"));
        if (raw.containsKey("--cache-meta"))       b.cacheMetaPath(raw.get("--cache-meta"));
        if (raw.containsKey("--out"))              b.outputPath(raw.get("--out"));
        if (raw.containsKey("--error-pattern"))    b.extraErrorPattern(raw.get("--error-pattern"));
        if (raw.containsKey("--expected-branch"))  b.expectedBranch(raw.get("--expected-branch"));
        if (raw.containsKey("--expected-build-id")) b.expectedBuildId(raw.get("--expected-build-id"));
        if (raw.containsKey("--min-artifact-bytes")) b.minArtifactBytes(longVal(raw, "--min-artifact-bytes", 0));
        if (raw.containsKey("--test-report"))      b.testReportFilename(raw.get("--test-report"));
        if (raw.containsKey("--require-provenance")) b.requireProvenance(true);

        if (raw.containsKey("--run-start")) {
            try { b.runStartTime(Instant.parse(raw.get("--run-start"))); }
            catch (Exception ignored) { /* best-effort */ }
        }

        if (raw.containsKey("--required")) {
            List<String> artifacts = Arrays.stream(raw.get("--required").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
            if (!artifacts.isEmpty()) b.requiredArtifacts(artifacts);
        }

        return b.build();
    }

    private static Map<String, String> tokenize(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String v = "true";
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    v = args[++i];
                }
                m.put(a, v);
            }
        }
        return m;
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required arg: " + key);
        return v;
    }

    private static int integer(Map<String, String> m, String key, int def) {
        try { return Integer.parseInt(m.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static long longVal(Map<String, String> m, String key, long def) {
        try { return Long.parseLong(m.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
}
