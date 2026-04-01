package com.fgd.core;

import com.fgd.config.FGDConfig;

import java.nio.file.Path;

/**
 * Immutable context object passed to every {@link com.fgd.checks.VerificationCheck}.
 *
 * Centralises all I/O paths and runtime metadata so individual checks remain
 * stateless and independently testable without touching global state.
 */
public final class CheckContext {

    private final FGDConfig config;
    private final Path artifactsDir;
    private final Path logsPath;
    private final Path cacheMetaPath;
    private final String commitId;
    private final int jobExitCode;

    public CheckContext(FGDConfig config) {
        this.config        = config;
        this.artifactsDir  = Path.of(config.artifactsDir());
        this.logsPath      = Path.of(config.logsPath());
        this.cacheMetaPath = Path.of(config.cacheMetaPath());
        this.commitId      = config.commitId();
        this.jobExitCode   = config.jobExitCode();
    }

    public FGDConfig config()        { return config; }
    public Path artifactsDir()       { return artifactsDir; }
    public Path logsPath()           { return logsPath; }
    public Path cacheMetaPath()      { return cacheMetaPath; }
    public String commitId()         { return commitId; }
    public int jobExitCode()         { return jobExitCode; }

    /** Resolves a relative filename against the artifacts directory. */
    public Path artifact(String filename) {
        return artifactsDir.resolve(filename);
    }
}
