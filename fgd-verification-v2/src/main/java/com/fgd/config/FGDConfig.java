package com.fgd.config;

import java.time.Instant;
import java.util.List;

/**
 * Validated, immutable configuration for a single FGD execution.
 *
 * Built by {@link CLIParser} from command-line arguments. All optional fields
 * have sensible defaults so FGD works out-of-the-box in standard CI layouts.
 */
public final class FGDConfig {

    // Required
    private final String commitId;

    // Paths
    private final String artifactsDir;
    private final String logsPath;
    private final String cacheMetaPath;
    private final String outputPath;

    // Artifact checks
    private final List<String> requiredArtifacts;
    private final long minArtifactBytes;
    private final String testReportFilename;

    // Log check
    private final String extraErrorPattern;

    // Cache check
    private final Instant runStartTime;

    // Provenance check
    private final boolean requireProvenance;
    private final String expectedBranch;
    private final String expectedBuildId;

    // Runtime
    private final int jobExitCode;

    private FGDConfig(Builder b) {
        this.commitId            = b.commitId;
        this.artifactsDir        = b.artifactsDir;
        this.logsPath            = b.logsPath;
        this.cacheMetaPath       = b.cacheMetaPath;
        this.outputPath          = b.outputPath;
        this.requiredArtifacts   = List.copyOf(b.requiredArtifacts);
        this.minArtifactBytes    = b.minArtifactBytes;
        this.testReportFilename  = b.testReportFilename;
        this.extraErrorPattern   = b.extraErrorPattern;
        this.runStartTime        = b.runStartTime;
        this.requireProvenance   = b.requireProvenance;
        this.expectedBranch      = b.expectedBranch;
        this.expectedBuildId     = b.expectedBuildId;
        this.jobExitCode         = b.jobExitCode;
    }

    public String commitId()            { return commitId; }
    public String artifactsDir()        { return artifactsDir; }
    public String logsPath()            { return logsPath; }
    public String cacheMetaPath()       { return cacheMetaPath; }
    public String outputPath()          { return outputPath; }
    public List<String> requiredArtifacts() { return requiredArtifacts; }
    public long minArtifactBytes()      { return minArtifactBytes; }
    public String testReportFilename()  { return testReportFilename; }
    public String extraErrorPattern()   { return extraErrorPattern; }
    public Instant runStartTime()       { return runStartTime; }
    public boolean requireProvenance()  { return requireProvenance; }
    public String expectedBranch()      { return expectedBranch; }
    public String expectedBuildId()     { return expectedBuildId; }
    public int jobExitCode()            { return jobExitCode; }

    // ---- builder ----

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String commitId;
        private String artifactsDir        = "artifacts";
        private String logsPath            = "logs/ci.log";
        private String cacheMetaPath       = "cache/metadata.json";
        private String outputPath;
        private List<String> requiredArtifacts = List.of(
                "test_report.xml", "build_artifact.bin", "provenance.json");
        private long minArtifactBytes      = 0;
        private String testReportFilename  = "test_report.xml";
        private String extraErrorPattern;
        private Instant runStartTime;
        private boolean requireProvenance  = false;
        private String expectedBranch;
        private String expectedBuildId;
        private int jobExitCode            = 0;

        private Builder() {}

        public Builder commitId(String v)              { this.commitId = v; return this; }
        public Builder artifactsDir(String v)          { this.artifactsDir = v; return this; }
        public Builder logsPath(String v)              { this.logsPath = v; return this; }
        public Builder cacheMetaPath(String v)         { this.cacheMetaPath = v; return this; }
        public Builder outputPath(String v)            { this.outputPath = v; return this; }
        public Builder requiredArtifacts(List<String> v) { this.requiredArtifacts = v; return this; }
        public Builder minArtifactBytes(long v)        { this.minArtifactBytes = v; return this; }
        public Builder testReportFilename(String v)    { this.testReportFilename = v; return this; }
        public Builder extraErrorPattern(String v)     { this.extraErrorPattern = v; return this; }
        public Builder runStartTime(Instant v)         { this.runStartTime = v; return this; }
        public Builder requireProvenance(boolean v)    { this.requireProvenance = v; return this; }
        public Builder expectedBranch(String v)        { this.expectedBranch = v; return this; }
        public Builder expectedBuildId(String v)       { this.expectedBuildId = v; return this; }
        public Builder jobExitCode(int v)              { this.jobExitCode = v; return this; }

        public FGDConfig build() {
            if (commitId == null || commitId.isBlank()) {
                throw new IllegalArgumentException("--commit is required");
            }
            if (outputPath == null) {
                outputPath = artifactsDir + "/fgd_result.json";
            }
            return new FGDConfig(this);
        }
    }
}
