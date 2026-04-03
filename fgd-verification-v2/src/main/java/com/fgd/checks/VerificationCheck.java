package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;

/**
 * Contract for a single, independently executable FGD verification check.
 *
 * Each implementation maps to one failure category from Table 1 of the paper.
 * Checks are stateless: all state is read from {@link CheckContext}, and the
 * result is returned as an immutable {@link CheckResult}. This allows checks to
 * be run in any order (or in parallel) and makes them independently unit-testable.
 */
public interface VerificationCheck {

    /** Human-readable name used in output JSON and logs. */
    String name();

    /**
     * Execute this check against the provided context.
     *
     * Must never throw — callers handle exceptions at the engine level.
     * Any I/O errors should be reflected as signals in the returned result.
     */
    CheckResult run(CheckContext ctx);
}
