package com.intellij.ide.starter

import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Path

private const val MAX_ARTIFACT_NAME_LENGTH_IN_BYTES = 240

class ArtifactNameTest {
  @Test
  fun `test directory names are flat and bounded`() {
    val nestedName = ReportingPathUtils.testDirectoryName("completion/bat/src/bin/bat/clap_app.rs")
    val longName = ReportingPathUtils.testDirectoryName("completion/bevy/crates/bevy_render/macros/src/as_bind_group.rs")

    nestedName shouldBe "completion-bat-src-bin-bat-clap_app.rs"
    longName.toByteArray(Charsets.UTF_8).size shouldBe 50
    longName.substringAfterLast('-').matches(Regex("[0-9a-f]{6}")) shouldBe true
    ReportingPathUtils.testDirectoryName("a/b") shouldBe ReportingPathUtils.testDirectoryName("a-b")
  }

  @Test
  fun `short flat test directory names stay unchanged`() {
    ReportingPathUtils.testDirectoryName("short-test") shouldBe "short-test"
  }

  /**
   * A path over the limit is reported and then used anyway: the run that built it is not the one that can shorten it, and the OS it runs on
   * may well take it.
   */
  @Test
  fun `paths exceeding the limit are reported outside CI`() {
    val path = Path.of("x".repeat(ReportingPathUtils.PATH_LENGTH_LIMIT))

    val reported = failuresReportedWhile {
      ReportingPathUtils.checkPathLength(path) shouldBe path
    }

    reported.single().message shouldContain "${ReportingPathUtils.PATH_LENGTH_LIMIT}-character limit"
    reported.single().kind shouldBe SyntheticTestKind.TEST_INFRA_EXCEPTION
  }

  /**
   * On an agent whose OS has no such limit the length is nobody's problem: a Bazel test run there is a couple of hundred characters deep
   * inside the output base before the test names anything, and reporting that would report every run.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `paths exceeding the limit are left alone on a CI agent that has no such limit`() {
    val path = Path.of("x".repeat(ReportingPathUtils.PATH_LENGTH_LIMIT))

    val reported = failuresReportedWhile(isBuildRunningOnCI = true) {
      ReportingPathUtils.checkPathLength(path) shouldBe path
    }

    reported.shouldBeEmpty()
  }

  @Test
  fun `short names stay unchanged`() {
    ReportingPathUtils.shortenWithHashIfNeeded("logs-short-test", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES) shouldBe "logs-short-test"
  }

  @Test
  fun `long artifact names leave room for the TeamCity suffix and zip extension`() {
    val testName = "testQualityGateFailureConditions-1-failure-conditions-severity-thresholds-severity-thresholds-any-null-" +
                   "critical-null-high-null-moderate-null-low-null-info-null-test-coverage-thresholds-test-coverage-thresholds-" +
                   "total-51-fresh-90-qodana"

    val artifactName = ReportingPathUtils.formatArtifactName("logs", testName)

    artifactName.toByteArray(Charsets.UTF_8).size shouldBe MAX_ARTIFACT_NAME_LENGTH_IN_BYTES
    "$artifactName-2147483647.zip".toByteArray(Charsets.UTF_8).size shouldBe 255
    artifactName.substringAfterLast('-').matches(Regex("[0-9a-f]{6}")) shouldBe true
  }

  @Test
  fun `the hash is stable and distinguishes names with the same prefix`() {
    val commonPrefix = "a".repeat(300)
    val first = ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-first", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)
    val second = ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-second", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)

    ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-first", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES) shouldBe first
    (first == second) shouldBe false
  }
}
