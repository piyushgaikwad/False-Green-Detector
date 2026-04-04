package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;
import com.fgd.core.VerificationSignal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Table 1 — Category: Artifact Integrity Failure.
 *
 * Validates that every required artifact listed in the FGD config:
 *   (a) exists on disk,
 *   (b) is non-empty, and
 *   (c) meets the minimum size threshold (if configured).
 *
 * Unlike v1, this check does NOT stop at the first missing file. It collects
 * violations across all required artifacts so the output JSON reflects the
 * complete failure picture.
 */
public final class ArtifactIntegrityCheck implements VerificationCheck {

    @Override
    public String name() {
        return "ArtifactIntegrityCheck";
    }

    @Override
    public CheckResult run(CheckContext ctx) {
        CheckResult.Builder result = CheckResult.builder(name());
        List<String> required = ctx.config().requiredArtifacts();

        for (String filename : required) {
            Path artifact = ctx.artifact(filename);

            if (!Files.exists(artifact)) {
                result.signal(VerificationSignal.MISSING_ARTIFACT)
                      .detail("missing_artifact", filename);
                continue;
            }

            try {
                long size = Files.size(artifact);
                result.detail("artifact_size_bytes_" + filename, size);

                if (size == 0) {
                    result.signal(VerificationSignal.EMPTY_ARTIFACT)
                          .detail("empty_artifact", filename);
                    continue;
                }

                long minBytes = ctx.config().minArtifactBytes();
                if (minBytes > 0 && size < minBytes) {
                    result.signal(VerificationSignal.CORRUPT_ARTIFACT)
                          .detail("undersized_artifact", filename)
                          .detail("expected_min_bytes", minBytes)
                          .detail("actual_bytes", size);
                }

            } catch (Exception e) {
                result.signal(VerificationSignal.CORRUPT_ARTIFACT)
                      .detail("artifact_io_error_" + filename, e.getMessage());
            }
        }

        return result.build();
    }
}
