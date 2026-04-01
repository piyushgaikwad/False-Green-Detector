package com.fgd.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result produced by a single {@link com.fgd.checks.VerificationCheck}.
 *
 * A check emits zero or more signals and attaches structured details used in
 * the FGD output JSON. Details are arbitrary key-value pairs; values must be
 * JSON-serialisable primitives (String, Number, Boolean, null).
 */
public final class CheckResult {

    private final String checkName;
    private final List<VerificationSignal> signals;
    private final Map<String, Object> details;

    private CheckResult(Builder b) {
        this.checkName = b.checkName;
        this.signals   = Collections.unmodifiableList(b.signals);
        this.details   = Collections.unmodifiableMap(b.details);
    }

    public String checkName()                { return checkName; }
    public List<VerificationSignal> signals() { return signals; }
    public Map<String, Object> details()     { return details; }
    public boolean passed()                  { return signals.isEmpty(); }

    // ---- builder ----

    public static Builder builder(String checkName) {
        return new Builder(checkName);
    }

    public static final class Builder {
        private final String checkName;
        private final List<VerificationSignal> signals = new java.util.ArrayList<>();
        private final Map<String, Object> details = new LinkedHashMap<>();

        private Builder(String checkName) { this.checkName = checkName; }

        public Builder signal(VerificationSignal s) {
            signals.add(s);
            return this;
        }

        public Builder detail(String key, Object value) {
            details.put(key, value);
            return this;
        }

        public CheckResult build() { return new CheckResult(this); }
    }
}
