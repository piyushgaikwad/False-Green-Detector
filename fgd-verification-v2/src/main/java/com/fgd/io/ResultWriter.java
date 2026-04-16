package com.fgd.io;

import com.fgd.core.CheckResult;
import com.fgd.core.VerificationResult;
import com.fgd.core.VerificationSignal;
import com.fgd.util.JsonUtil;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Serialises a {@link VerificationResult} to the stable FGD JSON output schema.
 *
 * Output format (stable across versions):
 * <pre>
 * {
 *   "fgd_version": "2",
 *   "verdict": "TRUE_GREEN" | "FALSE_GREEN",
 *   "commit": "<sha>",
 *   "timestamp": "<iso8601>",
 *   "signals": ["SIGNAL_A", ...],
 *   "checks": [
 *     {
 *       "name": "<CheckName>",
 *       "passed": true|false,
 *       "signals": [...],
 *       "details": { ... }
 *     }, ...
 *   ]
 * }
 * </pre>
 */
public final class ResultWriter {

    private static final String FGD_VERSION = "2";

    private ResultWriter() {}

    public static void write(VerificationResult result, String outputPath, PrintStream stdout)
            throws Exception {
        String json = toJson(result);

        Path outFile = Path.of(outputPath);
        Files.createDirectories(outFile.getParent());
        Files.writeString(outFile, json, StandardCharsets.UTF_8);

        stdout.println(json);
    }

    static String toJson(VerificationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"fgd_version\":\"").append(FGD_VERSION).append("\",\n");
        sb.append("  \"verdict\":\"").append(result.verdict().name()).append("\",\n");
        sb.append("  \"commit\":\"").append(JsonUtil.escape(result.commit())).append("\",\n");
        sb.append("  \"timestamp\":\"").append(result.timestamp().toString()).append("\",\n");

        // Flat signal list (deduplicated, across all checks)
        sb.append("  \"signals\":");
        appendSignalArray(sb, result.allSignals());
        sb.append(",\n");

        // Per-check breakdown
        sb.append("  \"checks\":[\n");
        List<CheckResult> checks = result.checkResults();
        for (int i = 0; i < checks.size(); i++) {
            appendCheck(sb, checks.get(i));
            if (i < checks.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static void appendCheck(StringBuilder sb, CheckResult check) {
        sb.append("    {\n");
        sb.append("      \"name\":\"").append(JsonUtil.escape(check.checkName())).append("\",\n");
        sb.append("      \"passed\":").append(check.passed()).append(",\n");
        sb.append("      \"signals\":");
        appendSignalArray(sb, check.signals());
        sb.append(",\n");
        sb.append("      \"details\":{");
        appendDetails(sb, check.details());
        sb.append("}\n");
        sb.append("    }");
    }

    private static void appendSignalArray(StringBuilder sb, List<VerificationSignal> signals) {
        sb.append("[");
        for (int i = 0; i < signals.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(signals.get(i).name()).append("\"");
        }
        sb.append("]");
    }

    private static void appendDetails(StringBuilder sb, Map<String, Object> details) {
        int k = 0;
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (k++ > 0) sb.append(",");
            sb.append("\"").append(JsonUtil.escape(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(JsonUtil.escape(val.toString())).append("\"");
            }
        }
    }
}
