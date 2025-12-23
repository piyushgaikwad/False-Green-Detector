# False-Green Detector (FGD)

FGD is a **post-CI verification component** that validates correctness evidence for CI runs that claim **SUCCESS**.  
It is **read-only, deterministic, and fast**, and it reclassifies “CI green” into:

- **TRUE-GREEN**: evidence is complete and consistent  
- **FALSE-GREEN**: CI reported success, but required evidence is missing/invalid (silent failure)

FGD is designed to be used with policy orchestration (e.g., Temporal) so that **only TRUE-GREEN results are promoted**.

---

## Why FGD exists

Modern CI systems can report green even when:
- test reports are missing or corrupt
- tests did not actually execute
- exit code is 0 but logs contain error signatures
- cache hits produce invalid outputs
- artifacts don’t match the commit/run

These are **silent failures** that inflate reported reliability.

---

## Design Goals

✅ **Deterministic**: same inputs → same verdict  
✅ **Read-only**: no retries, no re-runs, no modifications  
✅ **Fast**: minimal overhead (seconds)  
✅ **Portable**: works with GitHub Actions, Jenkins, etc.  
✅ **Policy-friendly**: emits machine-readable signals for orchestration

---

## Where FGD runs

FGD runs **after the CI engine reports SUCCESS** and **before** any:
- retry decision
- promotion to GitOps repo
- downstream deployment

**Canonical flow:**

<img width="568" height="703" alt="Screenshot 2025-12-23 at 3 38 36 PM" src="https://github.com/user-attachments/assets/97a80f78-9008-4a40-b1a3-16c023474ac5" />


---

## What FGD does (Checklist)

### 1) Evidence presence checks
Verify all required outputs exist (project-specific ruleset), e.g.
- test reports: `junit.xml`, `pytest.xml`, `surefire-reports/*`
- coverage: `coverage.xml`, `coverage.json`, `lcov.info`
- build artifacts: binaries, jars, image digest file, SBOM
- metadata: manifest, checksums, provenance, version stamp

**Signal:** `MISSING_ARTIFACT`

### 2) Evidence integrity checks
Validate outputs are parseable and non-corrupt
- XML/JSON parses successfully
- files are non-empty
- checksums match (optional)
- size ranges (optional)

**Signals:** `CORRUPT_TEST_REPORT`, `CORRUPT_COVERAGE`, `INVALID_JSON`

### 3) “Tests actually ran” checks
Detect green without real execution
- test count > 0
- duration > 0 (or above a floor)
- not suspiciously low test count
- required groups present (unit/integration)

**Signals:** `TESTS_NOT_EXECUTED`, `SUSPICIOUSLY_LOW_TEST_COUNT`

### 4) Log + exit-code consistency checks
Catch exit code 0 while logs show errors
- scan logs for known failure signatures:
  - “No tests found”
  - “Permission denied”
  - “Out of space”
  - “Segmentation fault”
  - “Killed”
  - tool-specific failure strings
- verify step-level exit codes if available

**Signals:** `IGNORED_ERROR`, `ERROR_SIGNATURE_WITH_EXIT_0`

### 5) Cache sanity checks
Detect “green” due to bad cache hits
- cache hit recorded but outputs invalid/missing
- outputs don’t match inputs (if hashes are tracked)
- stale artifact version mismatch (commit id differs)

**Signals:** `CACHE_SANITY_FAIL`, `STALE_OUTPUT`

### 6) Artifact provenance checks
Tie artifacts to the exact commit/run
- commit SHA in manifest matches `commit_id`
- build/version stamp matches the commit
- container digest file exists and matches push (if applicable)

**Signal:** `PROVENANCE_MISMATCH`

### 7) Policy-facing classification output
FGD returns (minimum):
- `verdict`: `TRUE_GREEN` or `FALSE_GREEN`
- `signals`: machine-readable reasons

Example:
```json
{ "verdict": "FALSE_GREEN", "signals": ["MISSING_ARTIFACT", "IGNORED_ERROR"] }

<img width="771" height="374" alt="Screenshot 2025-12-23 at 3 03 34 PM" src="https://github.com/user-attachments/assets/4c08c579-6e08-486c-96c2-cee278907218" />

