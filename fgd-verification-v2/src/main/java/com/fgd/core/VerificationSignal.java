package com.fgd.core;

/**
 * Canonical signal codes emitted by FGD checks.
 *
 * Each value maps to one failure category from the False-Green Failure Taxonomy
 * (Table 1 of the paper). Signals are additive — a single CI run can trigger
 * multiple signals across independent checks.
 */
public enum VerificationSignal {

    // Table 1 — Category: Missing Test Evidence
    MISSING_TEST_REPORT,
    TESTS_NOT_EXECUTED,
    EMPTY_TEST_REPORT,

    // Table 1 — Category: Log–Exit Code Inconsistency
    LOG_EXIT_CODE_INCONSISTENCY,

    // Table 1 — Category: Artifact Integrity Failure
    MISSING_ARTIFACT,
    EMPTY_ARTIFACT,
    CORRUPT_ARTIFACT,

    // Table 1 — Category: Cache-Induced Failure
    CACHE_SANITY_FAIL,

    // Table 1 — Category: Provenance Mismatch
    PROVENANCE_MISMATCH;
}
