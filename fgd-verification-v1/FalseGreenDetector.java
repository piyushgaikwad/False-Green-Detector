import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class FalseGreenDetector {

    // Exit codes (CI-friendly)
    // 0 = TRUE_GREEN, 2 = FALSE_GREEN, 3 = FGD_ERROR (tool failure)
    private static final int EXIT_TRUE_GREEN = 0;
    private static final int EXIT_FALSE_GREEN = 2;
    private static final int EXIT_FGD_ERROR  = 3;

    // Signals
    // (FGD ruleset)
    private static final String MISSING_ARTIFACT      = "MISSING_ARTIFACT";
    private static final String CORRUPT_TEST_REPORT   = "CORRUPT_TEST_REPORT";
    private static final String TESTS_NOT_EXECUTED    = "TESTS_NOT_EXECUTED";
    private static final String IGNORED_ERROR         = "IGNORED_ERROR";
    private static final String CACHE_SANITY_FAIL     = "CACHE_SANITY_FAIL";
    private static final String PROVENANCE_MISMATCH   = "PROVENANCE_MISMATCH";

    public static void main(String[] args) {
        try {
            Map<String, String> cli = parseArgs(args);

            String commitId = required(cli, "--commit");
            String artifactsDir = cli.getOrDefault("--artifacts-dir", "artifacts");
            String logsPath = cli.getOrDefault("--logs", "logs/ci.log");
            String cacheMetaPath = cli.getOrDefault("--cache-meta", "cache/metadata.json");
            String outPath = cli.getOrDefault("--out", artifactsDir + "/fgd_result.json");

            // Required artifacts list can be passed via --required comma-separated,
            // otherwise we use a sensible default for your paper.
            List<String> requiredArtifacts = new ArrayList<>();
            String required = cli.getOrDefault("--required",
                    "test_report.xml,build_artifact.bin,provenance.json");
            for (String item : required.split(",")) {
                requiredArtifacts.add(item.trim());
            }

            // Inputs (optional)
            int jobExitCode = Integer.parseInt(cli.getOrDefault("--exit-code", "0"));

            List<String> signals = new ArrayList<>();
            Map<String, Object> details = new LinkedHashMap<>();

            // 1) Evidence presence checks
            for (String rel : requiredArtifacts) {
                Path p = Paths.get(artifactsDir, rel);
                if (!Files.exists(p)) {
                    signals.add(MISSING_ARTIFACT);
                    details.put("missing", rel);
                    // keep collecting? For determinism, we can stop at first missing
                    break;
                }
                // Also ensure non-empty for key files
                if (Files.isRegularFile(p) && Files.size(p) == 0) {
                    signals.add(MISSING_ARTIFACT);
                    details.put("empty", rel);
                    break;
                }
            }

            // Only run deeper checks if required artifacts seem present
            boolean hasMissing = signals.contains(MISSING_ARTIFACT);

            // 2) Evidence integrity + 
            // 3) tests executed (JUnit XML)
            if (!hasMissing) {
                Path junit = Paths.get(artifactsDir, "test_report.xml");
                if (Files.exists(junit)) {
                    try {
                        long tests = parseJUnitTests(junit);
                        details.put("junit_tests", tests);
                        if (tests == 0) {
                            signals.add(TESTS_NOT_EXECUTED);
                        }
                    } catch (Exception e) {
                        signals.add(CORRUPT_TEST_REPORT);
                        details.put("junit_parse_error", e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
            }

            // 4) Log + exit-code consistency (ignored error)
            if (Files.exists(Paths.get(logsPath)) && jobExitCode == 0) {
                String logs = readFile(logsPath);

                // Keep patterns small & explicit; extend via --error-pattern if needed
                List<Pattern> errorPatterns = new ArrayList<>();
                errorPatterns.add(Pattern.compile("No tests found", Pattern.CASE_INSENSITIVE));
                errorPatterns.add(Pattern.compile("Permission denied", Pattern.CASE_INSENSITIVE));
                errorPatterns.add(Pattern.compile("Out of space", Pattern.CASE_INSENSITIVE));
                errorPatterns.add(Pattern.compile("Segmentation fault", Pattern.CASE_INSENSITIVE));
                errorPatterns.add(Pattern.compile("\\bKilled\\b", Pattern.CASE_INSENSITIVE));
                errorPatterns.add(Pattern.compile("\\bERROR\\b", Pattern.CASE_INSENSITIVE));

                String extraPattern = cli.get("--error-pattern");
                if (extraPattern != null && !extraPattern.isBlank()) {
                    errorPatterns.add(Pattern.compile(extraPattern));
                }

                String matched = firstMatch(logs, errorPatterns);
                if (matched != null) {
                    signals.add(IGNORED_ERROR);
                    details.put("ignored_error_match", matched);
                }
            }

            // 5) Cache sanity checks
            // metadata.json example:
            // { "hit": true, "outputValid": false }
            if (Files.exists(Paths.get(cacheMetaPath))) {
                String json = readFile(cacheMetaPath);
                Boolean hit = jsonBoolean(json, "hit");
                Boolean outputValid = jsonBoolean(json, "outputValid");
                details.put("cache_hit", hit);
                details.put("cache_outputValid", outputValid);

                if (Boolean.TRUE.equals(hit) && Boolean.FALSE.equals(outputValid)) {
                    signals.add(CACHE_SANITY_FAIL);
                }
            }

            // 6) Provenance mismatch checks
            // provenance.json example:
            // { "commit": "abc123", "imageDigest": "sha256:..." }
            Path prov = Paths.get(artifactsDir, "provenance.json");
            if (Files.exists(prov)) {
                String json = readFile(prov.toString());
                String provCommit = jsonString(json, "commit");
                details.put("provenance_commit", provCommit);

                if (provCommit != null && !provCommit.equals(commitId)) {
                    signals.add(PROVENANCE_MISMATCH);
                }
            }

            // Verdict
            String verdict = signals.isEmpty() ? "TRUE_GREEN" : "FALSE_GREEN";

            // Output JSON (stable schema)
            String outJson = buildJsonOutput(verdict, signals, commitId, details);

            // Ensure output directory exists
            Path outFile = Paths.get(outPath);
            Files.createDirectories(outFile.getParent());
            Files.writeString(outFile, outJson, StandardCharsets.UTF_8);

            // Also print to stdout (CI logs)
            System.out.println(outJson);

            // Exit code according to verdict
            if ("TRUE_GREEN".equals(verdict)) {
                System.exit(EXIT_TRUE_GREEN);
            } else {
                System.exit(EXIT_FALSE_GREEN);
            }

        } catch (Exception e) {
            // Tool-level failure
            String err = "{"
                    + "\"verdict\":\"FGD_ERROR\","
                    + "\"signals\":[\"FGD_ERROR\"],"
                    + "\"error\":\"" + escape(e.getClass().getSimpleName() + ": " + e.getMessage()) + "\""
                    + "}";
            System.err.println(err);
            System.exit(EXIT_FGD_ERROR);
        }
    }

    // ----------------- core checks -----------------

    private static long parseJUnitTests(Path junitPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // safer defaults
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setExpandEntityReferences(false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(junitPath)) {
            Document doc = db.parse(in);
            Element root = doc.getDocumentElement();

            // JUnit can be <testsuite> or <testsuites>
            if (root == null) return 0;

            String tag = root.getTagName();
            if ("testsuite".equalsIgnoreCase(tag)) {
                return parseTestsAttr(root);
            }
            if ("testsuites".equalsIgnoreCase(tag)) {
                // may have tests attr or multiple child suites
                long t = parseTestsAttr(root);
                if (t > 0) return t;
                // fallback: sum child testsuite attributes
                var nodes = root.getElementsByTagName("testsuite");
                long sum = 0;
                for (int i = 0; i < nodes.getLength(); i++) {
                    if (nodes.item(i) instanceof Element el) {
                        sum += parseTestsAttr(el);
                    }
                }
                return sum;
            }

            // Unknown format: treat as corrupt
            throw new IllegalArgumentException("Unsupported JUnit root element: " + tag);
        }
    }

    private static long parseTestsAttr(Element el) {
        String tests = el.getAttribute("tests");
        if (tests == null || tests.isBlank()) return 0;
        try {
            return Long.parseLong(tests.trim());
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    // ----------------- tiny JSON helpers (simple + deterministic) -----------------
    // For research tooling, keep dependencies minimal.
    // These are not general-purpose JSON parsers; they work for small, known metadata files.

    private static String jsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Boolean jsonBoolean(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return Boolean.parseBoolean(m.group(1).toLowerCase(Locale.ROOT));
    }

    private static String firstMatch(String text, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) return p.pattern();
        }
        return null;
    }

    // ----------------- CLI + IO helpers -----------------

    private static Map<String, String> parseArgs(String[] args) {
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

    private static String required(Map<String, String> cli, String key) {
        String v = cli.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing required arg: " + key);
        }
        return v;
    }

    private static String readFile(String p) throws IOException {
        return Files.readString(Paths.get(p), StandardCharsets.UTF_8);
    }

    private static String buildJsonOutput(String verdict, List<String> signals, String commit, Map<String, Object> details) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"verdict\":\"").append(escape(verdict)).append("\",");
        sb.append("\"signals\":[");
        for (int i = 0; i < signals.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(signals.get(i))).append("\"");
        }
        sb.append("],");
        sb.append("\"commit\":\"").append(escape(commit)).append("\",");

        sb.append("\"details\":{");
        int k = 0;
        for (Map.Entry<String, Object> e : details.entrySet()) {
            if (k++ > 0) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object val = e.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val.toString());
            } else {
                sb.append("\"").append(escape(val.toString())).append("\"");
            }
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
