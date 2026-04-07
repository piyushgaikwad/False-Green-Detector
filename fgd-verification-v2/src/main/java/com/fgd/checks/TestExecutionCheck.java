package com.fgd.checks;

import com.fgd.core.CheckContext;
import com.fgd.core.CheckResult;
import com.fgd.core.VerificationSignal;
import com.fgd.util.JUnitXmlParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Table 1 — Category: Missing Test Evidence.
 *
 * Validates that:
 *   (a) the JUnit XML test report file exists and is non-empty,
 *   (b) the report is parseable as valid JUnit XML,
 *   (c) at least one test was executed (tests attribute > 0).
 *
 * Zero-test counts are a canonical false-green signal: the CI pipeline ran
 * "successfully" but no tests were actually exercised.
 */
public final class TestExecutionCheck implements VerificationCheck {

    @Override
    public String name() {
        return "TestExecutionCheck";
    }

    @Override
    public CheckResult run(CheckContext ctx) {
        CheckResult.Builder result = CheckResult.builder(name());
        String reportFilename = ctx.config().testReportFilename();
        Path reportPath = ctx.artifact(reportFilename);

        if (!Files.exists(reportPath)) {
            return result.signal(VerificationSignal.MISSING_TEST_REPORT)
                         .detail("expected_report", reportPath.toString())
                         .build();
        }

        try {
            long size = Files.size(reportPath);
            if (size == 0) {
                return result.signal(VerificationSignal.EMPTY_TEST_REPORT)
                             .detail("report_file", reportFilename)
                             .build();
            }
        } catch (Exception e) {
            return result.signal(VerificationSignal.MISSING_TEST_REPORT)
                         .detail("report_io_error", e.getMessage())
                         .build();
        }

        try {
            JUnitXmlParser.ParsedReport report = JUnitXmlParser.parse(reportPath);
            result.detail("tests_total", report.totalTests())
                  .detail("tests_failures", report.failures())
                  .detail("tests_errors", report.errors())
                  .detail("tests_skipped", report.skipped());

            if (report.totalTests() == 0) {
                result.signal(VerificationSignal.TESTS_NOT_EXECUTED)
                      .detail("reason", "zero tests reported in JUnit XML");
            }
        } catch (Exception e) {
            result.signal(VerificationSignal.MISSING_TEST_REPORT)
                  .detail("report_parse_error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        return result.build();
    }
}
