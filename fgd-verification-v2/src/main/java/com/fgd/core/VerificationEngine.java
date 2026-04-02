package com.fgd.core;

import com.fgd.checks.VerificationCheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates all registered {@link VerificationCheck}s against a
 * {@link CheckContext} and produces an aggregated {@link VerificationResult}.
 *
 * Design: all checks always run (no early-exit on first failure) so the output
 * captures the complete failure picture for a given CI run. This matters for
 * the policy orchestrator, which needs the full signal set to decide whether to
 * retry, quarantine, or terminate.
 */
public final class VerificationEngine {

    private final List<VerificationCheck> checks;

    public VerificationEngine(List<VerificationCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    public VerificationResult run(CheckContext ctx) {
        List<CheckResult> results = new ArrayList<>();

        for (VerificationCheck check : checks) {
            CheckResult result;
            try {
                result = check.run(ctx);
            } catch (Exception e) {
                // A check that throws is treated as a tool-level error signal so
                // the remaining checks can still complete.
                result = CheckResult.builder(check.name())
                        .signal(VerificationSignal.CORRUPT_ARTIFACT) // sentinel for unexpected check failure
                        .detail("check_exception", e.getClass().getSimpleName() + ": " + e.getMessage())
                        .build();
            }
            results.add(result);
        }

        boolean anySignals = results.stream().anyMatch(r -> !r.passed());
        Verdict verdict = anySignals ? Verdict.FALSE_GREEN : Verdict.TRUE_GREEN;

        return new VerificationResult(verdict, ctx.commitId(), Instant.now(), results);
    }
}
