package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;
import com.fgd.core.VerificationSignal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Table 1 — Category: Log–Exit Code Inconsistency.
 *
 * Detects the case where the CI job exits with code 0 (success) but the
 * captured log output contains failure-indicative keywords. This pattern arises
 * when scripts swallow errors (e.g. "|| true"), when test harnesses misreport
 * exit codes, or when CI steps ignore non-zero sub-process returns.
 *
 * Patterns are evaluated against the full log text. All matching patterns are
 * recorded so the output JSON surfaces the complete evidence for the violation.
 * Additional patterns can be injected at runtime via {@link com.fgd.config.FGDConfig}.
 */
public final class LogExitCodeCheck implements VerificationCheck {

    // Baseline error patterns derived from common CI failure signals (paper §I.D).
    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            Pattern.compile("\\bFAILED\\b",           Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bERROR\\b",             Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bException\\b"),
            Pattern.compile("No tests found",          Pattern.CASE_INSENSITIVE),
            Pattern.compile("BUILD FAILURE",           Pattern.CASE_INSENSITIVE),
            Pattern.compile("Segmentation fault",      Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bKilled\\b",            Pattern.CASE_INSENSITIVE),
            Pattern.compile("Out of (memory|space)",   Pattern.CASE_INSENSITIVE),
            Pattern.compile("Permission denied",       Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bFATAL\\b",             Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String name() {
        return "LogExitCodeCheck";
    }

    @Override
    public CheckResult run(CheckContext ctx) {
        CheckResult.Builder result = CheckResult.builder(name());

        // This check is only meaningful when CI reported success (exit code 0).
        if (ctx.jobExitCode() != 0) {
            result.detail("skipped_reason", "job_exit_code=" + ctx.jobExitCode() + " (not 0)");
            return result.build();
        }

        Path logFile = ctx.logsPath();
        if (!Files.exists(logFile)) {
            result.detail("log_file_absent", logFile.toString());
            return result.build();
        }

        String logContent;
        try {
            logContent = Files.readString(logFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            result.detail("log_read_error", e.getMessage());
            return result.build();
        }

        List<Pattern> patterns = buildPatterns(ctx);
        List<String> matched = new ArrayList<>();

        for (Pattern p : patterns) {
            Matcher m = p.matcher(logContent);
            if (m.find()) {
                matched.add(p.pattern());
            }
        }

        result.detail("log_file", logFile.toString())
              .detail("patterns_evaluated", patterns.size());

        if (!matched.isEmpty()) {
            result.signal(VerificationSignal.LOG_EXIT_CODE_INCONSISTENCY)
                  .detail("matched_patterns", String.join(", ", matched))
                  .detail("match_count", matched.size());
        }

        return result.build();
    }

    private List<Pattern> buildPatterns(CheckContext ctx) {
        List<Pattern> all = new ArrayList<>(DEFAULT_PATTERNS);
        String extra = ctx.config().extraErrorPattern();
        if (extra != null && !extra.isBlank()) {
            all.add(Pattern.compile(extra));
        }
        return all;
    }
}
