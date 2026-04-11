package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;
import com.fgd.core.VerificationSignal;
import com.fgd.util.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

/**
 * Table 1 — Category: Cache-Induced Failure.
 *
 * Detects stale-cache scenarios where cached outputs are reused without
 * corresponding re-execution. Two complementary strategies are applied:
 *
 * Strategy A — Metadata check:
 *   Reads cache/metadata.json (produced by the CI cache layer) and checks
 *   that if "hit" is true, "outputValid" is also true. A hit with invalid
 *   output means stale cache content was served as a fresh build result.
 *
 * Strategy B — Timestamp check:
 *   For each required artifact, verifies that the file's last-modified time
 *   is NOT earlier than the CI run start time. Artifacts with timestamps
 *   predating the run indicate cache reuse with skipped execution.
 */
public final class CacheSanityCheck implements VerificationCheck {

    @Override
    public String name() {
        return "CacheSanityCheck";
    }

    @Override
    public CheckResult run(CheckContext ctx) {
        CheckResult.Builder result = CheckResult.builder(name());

        checkCacheMetadata(ctx, result);
        checkArtifactTimestamps(ctx, result);

        return result.build();
    }

    private void checkCacheMetadata(CheckContext ctx, CheckResult.Builder result) {
        Path metaFile = ctx.cacheMetaPath();
        if (!Files.exists(metaFile)) {
            result.detail("cache_metadata_absent", metaFile.toString());
            return;
        }

        try {
            String json = Files.readString(metaFile, StandardCharsets.UTF_8);
            Boolean hit = JsonUtil.readBoolean(json, "hit");
            Boolean outputValid = JsonUtil.readBoolean(json, "outputValid");
            String cacheKey = JsonUtil.readString(json, "cacheKey");

            result.detail("cache_hit", hit)
                  .detail("cache_output_valid", outputValid)
                  .detail("cache_key", cacheKey);

            if (Boolean.TRUE.equals(hit) && Boolean.FALSE.equals(outputValid)) {
                result.signal(VerificationSignal.CACHE_SANITY_FAIL)
                      .detail("cache_fail_reason", "cache hit with outputValid=false");
            }

        } catch (Exception e) {
            result.detail("cache_metadata_read_error", e.getMessage());
        }
    }

    private void checkArtifactTimestamps(CheckContext ctx, CheckResult.Builder result) {
        Instant runStart = ctx.config().runStartTime();
        if (runStart == null) {
            result.detail("timestamp_check_skipped", "run_start_time not configured");
            return;
        }

        List<String> required = ctx.config().requiredArtifacts();
        for (String filename : required) {
            Path artifact = ctx.artifact(filename);
            if (!Files.exists(artifact)) continue;

            try {
                FileTime lastModified = Files.getLastModifiedTime(artifact);
                Instant modifiedAt = lastModified.toInstant();
                result.detail("artifact_mtime_" + filename, modifiedAt.toString());

                if (modifiedAt.isBefore(runStart)) {
                    result.signal(VerificationSignal.CACHE_SANITY_FAIL)
                          .detail("stale_artifact", filename)
                          .detail("artifact_mtime", modifiedAt.toString())
                          .detail("run_start_time", runStart.toString());
                }
            } catch (Exception e) {
                result.detail("timestamp_check_error_" + filename, e.getMessage());
            }
        }
    }
}
