package com.fgd;

import com.fgd.checks.*;
import com.fgd.config.CLIParser;
import com.fgd.config.FGDConfig;
import com.fgd.core.*;
import com.fgd.io.ResultWriter;
import com.fgd.util.JsonUtil;

import java.util.List;

/**
 * False-Green Detector (FGD) — v2
 *
 * Entry point for the deterministic post-CI verification layer described in:
 *   "Beyond Green Builds: True-Green Verification and Policy-Driven Reliability
 *    in CI Pipelines" (Gaikwad & Patil, IEEE Access).
 *
 * FGD consumes CI-produced evidence (artifacts, test reports, logs, cache
 * metadata, provenance) and reclassifies CI-reported success as either
 * TRUE_GREEN (verified) or FALSE_GREEN (silent failure). The result is written
 * to a JSON file and echoed to stdout for consumption by the policy orchestrator
 * (e.g. Temporal workflow).
 *
 * Exit codes:
 *   0 — TRUE_GREEN
 *   2 — FALSE_GREEN
 *   3 — FGD_ERROR (tool-level failure, not a CI verdict)
 *
 * Usage:
 *   java -jar fgd.jar \
 *     --commit <sha> \
 *     [--artifacts-dir <path>]     default: artifacts/
 *     [--logs <path>]              default: logs/ci.log
 *     [--cache-meta <path>]        default: cache/metadata.json
 *     [--out <path>]               default: artifacts/fgd_result.json
 *     [--required <csv>]           default: test_report.xml,build_artifact.bin,provenance.json
 *     [--test-report <filename>]   default: test_report.xml
 *     [--exit-code <int>]          CI job exit code, default: 0
 *     [--error-pattern <regex>]    extra log error pattern
 *     [--min-artifact-bytes <n>]   minimum artifact size threshold
 *     [--run-start <iso8601>]      CI run start time for cache timestamp check
 *     [--require-provenance]       treat absent provenance.json as a signal
 *     [--expected-branch <name>]   assert provenance branch
 *     [--expected-build-id <id>]   assert provenance build ID
 */
public final class FalseGreenDetector {

    public static void main(String[] args) {
        try {
            FGDConfig config = CLIParser.parse(args);

            CheckContext ctx = new CheckContext(config);

            // Register checks — each maps to one failure category from Table 1.
            // Order is stable (affects output JSON only, not the verdict logic).
            VerificationEngine engine = new VerificationEngine(List.of(
                    new ArtifactIntegrityCheck(),
                    new TestExecutionCheck(),
                    new LogExitCodeCheck(),
                    new CacheSanityCheck(),
                    new ProvenanceCheck()
            ));

            VerificationResult result = engine.run(ctx);

            ResultWriter.write(result, config.outputPath(), System.out);

            System.exit(result.verdict().exitCode);

        } catch (IllegalArgumentException e) {
            // Configuration / CLI usage error
            String err = "{\"verdict\":\"FGD_ERROR\",\"signals\":[\"FGD_ERROR\"],"
                    + "\"error\":\"" + JsonUtil.escape(e.getMessage()) + "\"}";
            System.err.println(err);
            System.exit(Verdict.FGD_ERROR.exitCode);

        } catch (Exception e) {
            // Unexpected tool-level failure
            String err = "{\"verdict\":\"FGD_ERROR\",\"signals\":[\"FGD_ERROR\"],"
                    + "\"error\":\"" + JsonUtil.escape(e.getClass().getSimpleName() + ": " + e.getMessage()) + "\"}";
            System.err.println(err);
            System.exit(Verdict.FGD_ERROR.exitCode);
        }
    }
}
