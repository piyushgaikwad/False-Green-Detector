package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;
import com.fgd.core.VerificationSignal;
import com.fgd.util.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Table 1 — Category: Provenance Mismatch.
 *
 * Verifies that the generated artifacts correspond to the triggering commit.
 * Reads provenance.json (written by the CI build step) and asserts that:
 *   (a) the "commit" field matches the expected commit SHA passed to FGD,
 *   (b) the "branch" field (if present) matches the expected branch, and
 *   (c) the "buildId" field (if present) matches the orchestrator-issued build ID.
 *
 * A mismatch indicates that a prior commit's artifacts were promoted (e.g. due
 * to aggressive caching or a mis-configured pipeline), which can result in the
 * wrong version being deployed or released.
 */
public final class ProvenanceCheck implements VerificationCheck {

    @Override
    public String name() {
        return "ProvenanceCheck";
    }

    @Override
    public CheckResult run(CheckContext ctx) {
        CheckResult.Builder result = CheckResult.builder(name());
        Path provFile = ctx.artifact("provenance.json");

        if (!Files.exists(provFile)) {
            // Provenance file absence is only a signal if the config requires it.
            if (ctx.config().requireProvenance()) {
                result.signal(VerificationSignal.PROVENANCE_MISMATCH)
                      .detail("provenance_absent", provFile.toString());
            } else {
                result.detail("provenance_check_skipped", "file absent and not required");
            }
            return result.build();
        }

        try {
            String json = Files.readString(provFile, StandardCharsets.UTF_8);

            String provenanceCommit = JsonUtil.readString(json, "commit");
            String provenanceBranch = JsonUtil.readString(json, "branch");
            String provenanceBuildId = JsonUtil.readString(json, "buildId");

            result.detail("provenance_commit", provenanceCommit)
                  .detail("provenance_branch", provenanceBranch)
                  .detail("provenance_build_id", provenanceBuildId)
                  .detail("expected_commit", ctx.commitId());

            if (provenanceCommit != null && !provenanceCommit.equals(ctx.commitId())) {
                result.signal(VerificationSignal.PROVENANCE_MISMATCH)
                      .detail("commit_mismatch", "expected=" + ctx.commitId()
                              + " actual=" + provenanceCommit);
            }

            String expectedBranch = ctx.config().expectedBranch();
            if (expectedBranch != null && provenanceBranch != null
                    && !expectedBranch.equals(provenanceBranch)) {
                result.signal(VerificationSignal.PROVENANCE_MISMATCH)
                      .detail("branch_mismatch", "expected=" + expectedBranch
                              + " actual=" + provenanceBranch);
            }

            String expectedBuildId = ctx.config().expectedBuildId();
            if (expectedBuildId != null && provenanceBuildId != null
                    && !expectedBuildId.equals(provenanceBuildId)) {
                result.signal(VerificationSignal.PROVENANCE_MISMATCH)
                      .detail("build_id_mismatch", "expected=" + expectedBuildId
                              + " actual=" + provenanceBuildId);
            }

        } catch (Exception e) {
            result.signal(VerificationSignal.PROVENANCE_MISMATCH)
                  .detail("provenance_read_error", e.getMessage());
        }

        return result.build();
    }
}
