package com.fgd.core;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated outcome of all FGD checks for a single CI run.
 *
 * Produced by {@link com.fgd.core.VerificationEngine} and consumed by
 * {@link com.fgd.io.ResultWriter} for JSON serialisation and by
 * {@link com.fgd.FalseGreenDetector} for the exit-code decision.
 */
public final class VerificationResult {

    private final Verdict verdict;
    private final String commit;
    private final Instant timestamp;
    private final List<CheckResult> checkResults;

    public VerificationResult(Verdict verdict, String commit, Instant timestamp,
                              List<CheckResult> checkResults) {
        this.verdict      = verdict;
        this.commit       = commit;
        this.timestamp    = timestamp;
        this.checkResults = Collections.unmodifiableList(checkResults);
    }

    public Verdict verdict()                    { return verdict; }
    public String commit()                      { return commit; }
    public Instant timestamp()                  { return timestamp; }
    public List<CheckResult> checkResults()     { return checkResults; }

    /** Flattened, deduplicated signal list across all checks. */
    public List<VerificationSignal> allSignals() {
        return checkResults.stream()
                .flatMap(r -> r.signals().stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
