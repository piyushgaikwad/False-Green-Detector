package com.fgd.core;

/**
 * Policy-actionable outcome emitted by FGD after evidence validation.
 *
 * TRUE_GREEN  — CI reported success AND all evidence checks passed.
 * FALSE_GREEN — CI reported success BUT one or more evidence checks failed.
 * FGD_ERROR   — FGD itself failed (tool-level, not a CI verdict).
 */
public enum Verdict {
    TRUE_GREEN(0),
    FALSE_GREEN(2),
    FGD_ERROR(3);

    public final int exitCode;

    Verdict(int exitCode) {
        this.exitCode = exitCode;
    }
}
