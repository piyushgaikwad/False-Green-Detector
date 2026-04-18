# FGD v2 — Low-Level Design

## Package Structure

```
com.fgd/
├── FalseGreenDetector.java          # Entry point — wires config → engine → output
│
├── core/
│   ├── Verdict.java                 # Enum: TRUE_GREEN(0) | FALSE_GREEN(2) | FGD_ERROR(3)
│   ├── VerificationSignal.java      # Enum: one value per Table-1 failure category signal
│   ├── CheckResult.java             # Immutable result from one check (signals + details)
│   ├── VerificationResult.java      # Aggregated result across all checks
│   ├── CheckContext.java            # Read-only I/O context passed to every check
│   └── VerificationEngine.java      # Runs all checks; aggregates → VerificationResult
│
├── checks/
│   ├── VerificationCheck.java       # Interface: name() + run(CheckContext) → CheckResult
│   ├── ArtifactIntegrityCheck.java  # Table 1: Artifact Integrity Failure
│   ├── TestExecutionCheck.java      # Table 1: Missing Test Evidence
│   ├── LogExitCodeCheck.java        # Table 1: Log–Exit Code Inconsistency
│   ├── CacheSanityCheck.java        # Table 1: Cache-Induced Failure
│   └── ProvenanceCheck.java         # Table 1: Provenance Mismatch
│
├── config/
│   ├── FGDConfig.java               # Immutable config (builder pattern)
│   └── CLIParser.java               # Tokenises argv → FGDConfig
│
├── io/
│   └── ResultWriter.java            # Serialises VerificationResult → JSON file + stdout
│
└── util/
    ├── JUnitXmlParser.java          # Secure JUnit XML reader (XXE disabled)
    └── JsonUtil.java                # Minimal regex JSON reader for CI metadata files
```

## Data Flow

```
argv
 │
 ▼
CLIParser.parse(argv)
 │  produces
 ▼
FGDConfig  ──────────────────────────────────────────────────┐
 │                                                            │
 ▼                                                            │
CheckContext(config)   ◄── all checks read I/O paths from here│
 │                                                            │
 ▼                                                            │
VerificationEngine.run(ctx)                                   │
 │                                                            │
 ├── ArtifactIntegrityCheck.run(ctx)  → CheckResult          │
 ├── TestExecutionCheck.run(ctx)      → CheckResult          │
 ├── LogExitCodeCheck.run(ctx)        → CheckResult          │
 ├── CacheSanityCheck.run(ctx)        → CheckResult          │
 └── ProvenanceCheck.run(ctx)         → CheckResult          │
          │                                                   │
          ▼  (all checks always run — no early-exit)         │
     VerificationResult                                       │
       verdict = TRUE_GREEN if no signals, else FALSE_GREEN   │
          │                                                   │
          ▼                                                   │
     ResultWriter.write(result, config.outputPath(), stdout)  │
          │                                                   │
          ▼                                                   │
     System.exit(verdict.exitCode)  ◄────────────────────────┘
          0 = TRUE_GREEN
          2 = FALSE_GREEN
          3 = FGD_ERROR
```

## Failure Taxonomy → Signal Mapping (Table 1)

| Paper Category              | VerificationCheck        | VerificationSignal(s)                                       |
|-----------------------------|--------------------------|-------------------------------------------------------------|
| Missing Test Evidence       | TestExecutionCheck       | MISSING_TEST_REPORT, EMPTY_TEST_REPORT, TESTS_NOT_EXECUTED  |
| Log–Exit Code Inconsistency | LogExitCodeCheck         | LOG_EXIT_CODE_INCONSISTENCY                                 |
| Artifact Integrity Failure  | ArtifactIntegrityCheck   | MISSING_ARTIFACT, EMPTY_ARTIFACT, CORRUPT_ARTIFACT          |
| Cache-Induced Failure       | CacheSanityCheck         | CACHE_SANITY_FAIL                                           |
| Provenance Mismatch         | ProvenanceCheck          | PROVENANCE_MISMATCH                                         |

## Output JSON Schema (v2)

```json
{
  "fgd_version": "2",
  "verdict": "TRUE_GREEN | FALSE_GREEN",
  "commit": "<sha>",
  "timestamp": "<iso8601>",
  "signals": ["SIGNAL_A"],
  "checks": [
    {
      "name": "ArtifactIntegrityCheck",
      "passed": true,
      "signals": [],
      "details": { "artifact_size_bytes_build_artifact.bin": 4096 }
    },
    {
      "name": "TestExecutionCheck",
      "passed": false,
      "signals": ["TESTS_NOT_EXECUTED"],
      "details": { "tests_total": 0, "tests_failures": 0 }
    }
  ]
}
```

## Key Design Decisions

**All checks run, no early-exit.**  
The policy orchestrator needs the full signal set to decide whether to retry
(transient/infra failure), quarantine (flaky tests), or terminate (evidence
violation). Stopping at the first signal would hide co-occurring failures.

**Checks are stateless.**  
Each check reads only from `CheckContext` and returns an immutable `CheckResult`.
This makes checks independently unit-testable without filesystem setup beyond
the single file they care about.

**Zero runtime dependencies.**  
FGD ships as a fat JAR. The only libraries used are JDK built-ins (javax.xml,
java.nio, java.util.regex). This ensures it runs in minimal CI containers and
air-gapped environments without classpath configuration.

**Stable exit codes.**  
Exit codes are owned by `Verdict` (not scattered through main). The policy
orchestrator keys its branching logic on these codes:
- `0` → promote to release pipeline
- `2` → block release, apply recovery policy
- `3` → FGD infrastructure failure, alert on-call

**CacheSanityCheck uses two strategies.**  
Metadata-based (reads `cache/metadata.json`) catches known cache layers that
expose an `outputValid` flag. Timestamp-based catches all others: an artifact
with a last-modified time predating the CI run start is necessarily stale.
