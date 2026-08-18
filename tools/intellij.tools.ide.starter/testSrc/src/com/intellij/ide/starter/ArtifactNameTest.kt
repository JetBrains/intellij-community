package com.intellij.ide.starter

import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.MAX_ARTIFACT_NAME_LENGTH_IN_BYTES
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldHaveMaxLength
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotMatch
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Path

private val NAME_HASH = Regex("[0-9a-f]{${ReportingPathUtils.NAME_HASH_LENGTH}}")
private const val SHORTER_TEMP_ROOT_SAVING: Int = 5

class ArtifactNameTest {
  @Test
  fun `test directory names are flat and bounded`() {
    val nestedName = ReportingPathUtils.testDirectoryName("completion/bat/src/bin/bat/clap_app.rs")
    val longName = ReportingPathUtils.testDirectoryName("completion/bevy/crates/bevy_render/macros/src/as_bind_group.rs")

    nestedName.shouldBe("completion-bat-src-bin-bat-clap_app.rs")
    longName.toByteArray(Charsets.UTF_8).size.shouldBeLessThanOrEqual(ReportingPathUtils.MAX_DIR_NAME_LENGTH_IN_BYTES)
    longName.substringAfterLast('-').shouldMatch(NAME_HASH)
    ReportingPathUtils.testDirectoryName("a/b").shouldBe(ReportingPathUtils.testDirectoryName("a-b"))
  }

  @Test
  fun `short flat test directory names stay unchanged`() {
    ReportingPathUtils.testDirectoryName("short-test").shouldBe("short-test")
  }

  /**
   * A path over the limit is reported and then used anyway: the run that built it is not the one that can shorten it, and the OS it runs on
   * may well take it.
   */
  @Test
  fun `paths exceeding the limit are reported outside CI`() {
    val path = Path.of("x".repeat(ReportingPathUtils.PATH_LENGTH_LIMIT))

    val reported = failuresReportedWhile {
      ReportingPathUtils.checkPathLength(path).shouldBe(path)
    }

    reported.single().message.shouldContain("${ReportingPathUtils.PATH_LENGTH_LIMIT}-character limit")
    reported.single().kind.shouldBe(SyntheticTestKind.TEST_INFRA_EXCEPTION)
  }

  /**
   * On an agent whose OS has no such limit the length is nobody's problem: a Bazel test run there is a couple of hundred characters deep
   * inside the output base before the test names anything, and reporting that would report every run.
   */
  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `paths exceeding the limit are left alone on a CI agent that has no such limit`() {
    val path = Path.of("x".repeat(ReportingPathUtils.PATH_LENGTH_LIMIT))
    val directory = directoryWithAbsoluteLength(245)
    val fileStem = "threadDump-1-09-53-17"

    val reported = failuresReportedWhile(isBuildRunningOnCI = true) {
      ReportingPathUtils.checkPathLength(path).shouldBe(path)
      ReportingPathUtils.shortenFileStemIn(
        directory,
        fileStem,
        extension = ".txt",
        preservedPrefix = "threadDump",
      ).shouldBe(fileStem)
    }

    reported.shouldBeEmpty()
  }

  @Test
  fun `the path limit policy covers every platform and environment`() {
    ReportingPathUtils.shouldEnforcePathLength(isWindows = true, isBuildRunningOnCI = true).shouldBe(true)
    ReportingPathUtils.shouldEnforcePathLength(isWindows = true, isBuildRunningOnCI = false).shouldBe(true)
    ReportingPathUtils.shouldEnforcePathLength(isWindows = false, isBuildRunningOnCI = false).shouldBe(true)
    ReportingPathUtils.shouldEnforcePathLength(isWindows = false, isBuildRunningOnCI = true).shouldBe(false)
  }

  /**
   * The rule the test above only decides: where the limit is not enforced, a stem is left whole, however deep the directory that will hold
   * it. Both sides of it are pinned here rather than on the one OS each side happens to hold, so that neither goes untested.
   */
  @Test
  fun `a stem is only shortened where the path limit is enforced`() {
    val fileStem = "threadDump-1-09-53-17"
    // deep enough that the stem has to shrink, and shallow enough that the prefix and the hash still fit
    val directory = directoryWithStemBudget(16, ".txt")

    val leftWhole = ReportingPathUtils.shortenFileStemIn(
      directory,
      fileStem,
      extension = ".txt",
      preservedPrefix = "threadDump",
      enforcePathLengthLimit = false,
    )
    val shortened = ReportingPathUtils.shortenFileStemIn(
      directory,
      fileStem,
      extension = ".txt",
      preservedPrefix = "threadDump",
      enforcePathLengthLimit = true,
    )

    leftWhole.shouldBe(fileStem)
    shortened.shouldNotBe(fileStem)
    shortened.shouldStartWith("threadDump-")
    shortened.substringAfterLast('-').shouldMatch(NAME_HASH)
  }

  /** A launch publishes into a directory of its own, so its artifacts only need a name that stays unique in time. */
  @Test
  fun `an unqualified artifact name is the type and the time of day`() {
    // the date the name goes without is 8 characters of a path that has Windows' limit to fit in
    ReportingPathUtils.formatArtifactName("logs").shouldMatch(Regex("logs-\\d{6}"))
  }

  @Test
  fun `short names stay unchanged`() {
    ReportingPathUtils.shortenWithHashIfNeeded("logs-short-test", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES).shouldBe("logs-short-test")
  }

  @Test
  fun `long artifact names leave room for the TeamCity suffix and zip extension`() {
    val testName = "testQualityGateFailureConditions-1-failure-conditions-severity-thresholds-severity-thresholds-any-null-" +
                   "critical-null-high-null-moderate-null-low-null-info-null-test-coverage-thresholds-test-coverage-thresholds-" +
                   "total-51-fresh-90-qodana"

    val artifactName = ReportingPathUtils.formatArtifactName("logs", testName)

    artifactName.toByteArray(Charsets.UTF_8).size.shouldBeLessThanOrEqual(MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)
    "$artifactName-2147483647.zip".toByteArray(Charsets.UTF_8).size.shouldBeLessThanOrEqual(255)
    artifactName.substringAfterLast('-').shouldMatch(NAME_HASH)
  }

  @Test
  fun `the hash is stable and distinguishes names with the same prefix`() {
    val commonPrefix = "a".repeat(300)
    val first = ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-first", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)
    val second = ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-second", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES)

    ReportingPathUtils.shortenWithHashIfNeeded("$commonPrefix-first", MAX_ARTIFACT_NAME_LENGTH_IN_BYTES).shouldBe(first)
    second.shouldNotBe(first)
  }

  @Test
  fun `artifact name formatted in a short directory stays whole`() {
    val directory = directoryWithAbsoluteLength(40)
    val artifactName = ReportingPathUtils.formatArtifactNameIn(directory, "async", "myTest", extension = ".jfr")
    artifactName.shouldStartWith("async-myTest-")
    artifactName.substringAfterLast('-').shouldNotMatch(NAME_HASH.toString())
    val fullPath = directory.resolve("$artifactName.jfr")
    fullPath.toAbsolutePath().normalize().toString().shouldHaveMaxLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 1)
  }

  /**
   * IJent WSL Performance Tests / Win11
   * [#2292](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_IJentWin11WslJpsPerformanceTests/1035689402?buildTab=tests)
   * produced a 308-character profiler path. A shorter temp root takes 5 characters off it, which leaves 303, so the path only fits once the
   * file stem is bounded against the directory as well.
   */
  @Test
  fun `the async profiler path from the IJent build is shortened to fit`() {
    val originalFileName = "async-spring-pet-clinic-gradle-indexing-Docker-123456.jfr"
    val originalPathLength = 308
    val directoryLength = originalPathLength - 1 - originalFileName.length - SHORTER_TEMP_ROOT_SAVING
    val directory = directoryWithAbsoluteLength(directoryLength)
    val testName = "spring-pet-clinic-gradle-indexing-Docker"
    val extension = ".jfr"

    val artifactName = ReportingPathUtils.formatArtifactNameIn(directory, "async", testName, extension = extension)

    val fullPath = directory.resolve("$artifactName$extension").toAbsolutePath().normalize()
    val legacyPathLength = directory.toString().length + SHORTER_TEMP_ROOT_SAVING + 1 + originalFileName.length
    legacyPathLength.shouldBe(originalPathLength)
    fullPath.toString().shouldHaveMaxLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 1)
    artifactName.substringAfterLast('-').shouldMatch(NAME_HASH)
  }

  // A test for the thread dump path of build 2292 stood here. The 269 characters it was built on held `monitoring-thread-dumps-ide`, which
  // `AT-4970` renamed to `thread-dumps-ide` and thereby shortened by 11 characters. That path is 253 characters today and needs nothing.
  // What the test covered of the bound itself, `a protected thread dump prefix is kept at its minimum path budget` covers below.

  @Test
  fun `a protected thread dump prefix is kept at its minimum path budget`() {
    val fileStem = "threadDump-1-09-53-17"
    val extension = ".txt"

    val tooSmall = ReportingPathUtils.shortenFileStemIn(
      directoryWithStemBudget(14, extension),
      fileStem,
      extension,
      preservedPrefix = "threadDump",
    )
    tooSmall.shouldBe(fileStem)

    for (pathBudget in 15..16) {
      val directory = directoryWithStemBudget(pathBudget, extension)
      val shortened = ReportingPathUtils.shortenFileStemIn(
        directory,
        fileStem,
        extension,
        preservedPrefix = "threadDump",
      )

      shortened.shouldStartWith("threadDump-")
      shortened.substringAfterLast('-').shouldMatch(NAME_HASH)
      directory.resolve("$shortened$extension")
        .toAbsolutePath()
        .normalize()
        .toString()
        .shouldHaveMaxLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 1)
    }
  }

  @Test
  fun `kill diagnostic prefixes are kept when their names are shortened`() {
    val diagnostics = listOf(
      Triple("threadDump-before-kill", "threadDump-before-kill-123456789", ".txt"),
      Triple("memoryDump-before-kill", "memoryDump-before-kill-123456789", ".hprof.gz"),
    )

    for ((prefix, fileStem, extension) in diagnostics) {
      val minimumBudget = prefix.length + 1 + ReportingPathUtils.NAME_HASH_LENGTH
      val directory = directoryWithStemBudget(minimumBudget, extension)
      val shortened = ReportingPathUtils.shortenFileStemIn(
        directory,
        fileStem,
        extension,
        preservedPrefix = prefix,
      )

      shortened.shouldStartWith("$prefix-")
      shortened.substringAfterLast('-').shouldMatch(NAME_HASH)
      directory.resolve("$shortened$extension")
        .toAbsolutePath()
        .normalize()
        .toString()
        .shouldHaveMaxLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 1)
    }
  }

  @Test
  fun `artifact name in a directory too deep to fit the hash is returned unmodified`() {
    val directory = directoryWithAbsoluteLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 5)
    val normalName = ReportingPathUtils.formatArtifactName("async", "myTest")
    val boundedName = ReportingPathUtils.formatArtifactNameIn(directory, "async", "myTest", extension = ".jfr")

    boundedName.shouldBe(normalName)
  }

  private fun directoryWithAbsoluteLength(targetLength: Int): Path {
    val root = Path.of("").toAbsolutePath().normalize().root ?: error("The current path has no root")
    val paddingLength = targetLength - root.toString().length
    require(paddingLength in 1..255)
    return root.resolve("d".repeat(paddingLength))
  }

  private fun directoryWithStemBudget(pathBudget: Int, extension: String): Path =
    directoryWithAbsoluteLength(ReportingPathUtils.PATH_LENGTH_LIMIT - 2 - extension.length - pathBudget)
}
