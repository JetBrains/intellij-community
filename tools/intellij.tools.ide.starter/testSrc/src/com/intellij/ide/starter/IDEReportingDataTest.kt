package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.ide.starter.runner.TestMethodReportingIdentity
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.ReportingPathUtils.MAX_DIR_NAME_LENGTH_IN_BYTES
import com.intellij.ide.starter.utils.ReportingPathUtils.MAX_LAUNCH_DIR_NAME_LENGTH_IN_BYTES
import com.intellij.ide.starter.utils.ReportingPathUtils.NAME_HASH_LENGTH
import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import java.nio.file.Path
import kotlin.io.path.exists

private const val LONG_NAME_PREFIX = "reused-ide-process-reports-into-a-dir-of-its-own-per-test-method"
private val HASH_SUFFIX = Regex("-[0-9a-f]{$NAME_HASH_LENGTH}$")

/** The length of `Z:\BuildAgent\temp\buildTmp\test<random>\ide-tests\tests\RM-LOCAL\diaspora-project-test` on a Windows CI agent. */
private const val CI_REPORTING_ROOT_LENGTH = 92

/** What the async profiler writes into `snapshots`: `<build>-<activity>-<timestamp>.jfr`. */
private const val PROFILER_SNAPSHOT_NAME = "RM-263.SNAPSHOT-completion-20260816193137.jfr"

/** The length of `Z:\BuildAgent\…\ide-tests\tests\IU-LOCAL\delegate-run-a-gradle-task-to-idea-test-f919` on a Windows CI agent. */
private const val CI_GRADLE_REPORTING_ROOT_LENGTH = 115

/** What the thread dump monitor writes below `log`, deeper than the reporting directories themselves. */
private const val THREAD_DUMP_NAME = "monitoring-thread-dumps-ide/threadDump-1-09-53-17.txt"

private fun longMethodName(distinguishedBy: String): String = "$LONG_NAME_PREFIX-$distinguishedBy"

/**
 * What one IDE launch is called and where it reports: a reused IDE process reports every test method into a directory of its own, named
 * after the method and cut to keep the path short, and publishes into that same tree below the test it belongs to.
 */
class IDEReportingDataTest {
  @TempDir
  lateinit var reportingRoot: Path

  // region What a launch is called

  @Test
  fun `a method name short enough to fit is prefixed with its execution index`() {
    methodDirNameOf("opens-a-project") shouldBe "1_opens-a-project"
  }

  @Test
  fun `long method names keep their index and get a stable distinguishing hash`() {
    val firstMethodName = longMethodName("first")
    val first = methodDirNameOf(firstMethodName, index = 1)
    val second = methodDirNameOf(longMethodName("second"), index = 2)

    first.toByteArray(Charsets.UTF_8).size shouldBeLessThanOrEqual MAX_DIR_NAME_LENGTH_IN_BYTES
    first.startsWith("1_") shouldBe true
    HASH_SUFFIX.containsMatchIn(first) shouldBe true
    methodDirNameOf(firstMethodName, index = 1) shouldBe first
    (first.removePrefix("1_") == second.removePrefix("2_")) shouldBe false
  }

  /** A reused IDE process registers the reporting data of a method again on every switch back to it. */
  @Test
  fun `the same method name always gets the same dir back`() {
    val methodName = longMethodName("switched-away-from-and-back-to")

    val firstRegistration = methodDirNameOf(methodName, index = 1)
    methodDirNameOf(longMethodName("running-in-between"), index = 2)

    methodDirNameOf(methodName, index = 1) shouldBe firstRegistration
  }

  @Test
  fun `slashes inside long parameterized display names stay in one bounded directory`() {
    val longParameterDescription = "test-case-ide-info-".repeat(20)
    val segments = reportingDataOf(
      testName = "qodana-test",
      testMethod = testMethodIdentity("QodanaTest/$longParameterDescription/lambda-id", index = 1),
    ).launchDirSegments()
    val parameterDirName = segments.last()

    segments.size shouldBe 1
    parameterDirName.toByteArray(Charsets.UTF_8).size shouldBeLessThanOrEqual MAX_DIR_NAME_LENGTH_IN_BYTES
    HASH_SUFFIX.containsMatchIn(parameterDirName) shouldBe true
  }

  @Test
  fun `the execution index prefixes the method inside the directory of its test`() {
    val reportingData = reportingDataOf(
      testName = "maven-smoke-tests",
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )

    reportingData.launchDirSegments() shouldBe listOf("1_add-custom-roots-in-maven-project")
    reportingData.artifactPath shouldBe "maven-smoke-tests/1-add-custom-roots-in-maven-project"
  }

  /**
   * A test name built from `CurrentTestMethod` names the class already, and a suffix of its own — a product code, say — must not hide
   * that. Spelling the class a second time cost a whole bounded directory name and pushed the JVM crash log of a CI run past the limit:
   * `…/AI-LOCAL/check-fus-…-witho-f69a9e/check-fus-…-witho-538b4e/1_open-project-without-settings/…` came to 278 characters, of which the
   * two directories that begin alike without being alike were 100.
   */
  @Test
  fun `a class the directory of the test already names is not repeated below it`() {
    val methodName = "CheckFusReopenStartupOpenProjectWithoutSettingsAITest/openProjectWithoutSettings"
    val reportingData = reportingDataOf(
      testName = "${methodName.hyphenateTestName()}-AI",
      testMethod = testMethodIdentity(methodName, index = 1),
      requestedLaunchName = "setupMetadataScheme",
    )

    reportingData.launchDirSegments() shouldBe listOf("1_open-project-without-settings", "setupMetadataScheme")
    // the test is still named once: what stops the class from being spelled twice must not stop the test from being spelled at all
    reportingData.artifactPath shouldBe
      "${ReportingPathUtils.testDirectoryName("${methodName.hyphenateTestName()}-AI")}/" +
      "1-open-project-without-settings/setupMetadataScheme"
  }

  /**
   * A test named after the whole method names the class too, so the class needs no directory below it. Keeping one cost 40 characters and
   * pushed a Gradle thread dump to exactly 260: `…\IU-LOCAL\delegate-run-a-gradle-task-to-idea-test-del-f9198e\
   * delegate-run-a-gradle-task-to-idea-test\1_delegate-run-a-gradle-task-to-idea-wsl\log\monitoring-thread-dumps-ide\threadDump-…txt`.
   */
  @Test
  fun `a class the test name spells out along with the method is not repeated below it either`() {
    val reportingData = gradleDelegateRunLaunch()

    reportingData.launchDirSegments() shouldBe listOf("1_delegate-run-a-gradle-task-to-idea-wsl")
    // the artifact path leaves the test out instead of the class, so there the class is what names the test
    reportingData.artifactPath shouldBe
      "delegate-run-a-gradle-task-to-idea-test/1-delegate-run-a-gradle-task-to-idea-wsl"
  }

  /**
   * The directory the test reports in is [ReportingPathUtils.testDirectoryName], cut to a bounded length, so a class the test name only
   * names further along is cut away with the rest of it — and leaving the class out below would name it nowhere.
   */
  @Test
  fun `a class the test name only names past its own bounded length is still spelled out`() {
    val testName = "a-project-with-a-rather-long-descriptive-name-maven-smoke-tests"
    val reportingData = reportingDataOf(
      testName = testName,
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )

    ReportingPathUtils.testDirectoryName(testName) shouldNotContain "maven-smoke-tests"
    reportingData.launchDirSegments() shouldBe listOf("maven-smoke-tests", "1_add-custom-roots-in-maven-project")
  }

  /**
   * A hyphenated name keeps the dots it was given, so a `Class.method` test name names its class every bit as much as a `Class/method`
   * one, whose slash becomes a hyphen. Spelling the class again below it would spend a whole bounded directory on what the level above
   * says already.
   */
  @Test
  fun `a class the test name names before a dot is not repeated below it`() {
    val reportingData = reportingDataOf(
      testName = "MavenSmokeTests.addCustomRootsInMavenProject",
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )

    reportingData.launchDirSegments() shouldBe listOf("1_add-custom-roots-in-maven-project")
  }

  @Test
  fun `a class the test name only begins like is still spelled out`() {
    val reportingData = reportingDataOf(
      testName = "maven-smoke-testsuite",
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )

    reportingData.launchDirSegments() shouldBe listOf("maven-smoke-tests", "1_add-custom-roots-in-maven-project")
  }

  @Test
  fun `published method artifacts keep their execution index when a method is reused`() {
    val registered = linkedMapOf<String, IDEReportingData>()
    val startup = registered.register("add-custom-roots-in-maven-project")
    val second = registered.register("add-source-folder-and-reopen-project")
    val startupAgain = registered.register("add-custom-roots-in-maven-project")

    startup.artifactPath shouldBe "add-custom-src-root-in-maven-project-test/1-add-custom-roots-in-maven-project"
    second.artifactPath shouldBe "add-custom-src-root-in-maven-project-test/2-add-source-folder-and-reopen-project"
    startupAgain.artifactPath shouldBe startup.artifactPath
  }

  @Test
  fun `a provided test name containing the method gets indexed in place whether hyphenated or not`() {
    val testMethodName = "MavenSmokeTests/addCustomRootsInMavenProject"
    val providedTestNames = listOf(
      "maven-smoke-tests/add-custom-roots-in-maven-project",
      testMethodName,
    )

    providedTestNames.forEach { providedTestName ->
      val reportingData = reportingDataOf(
        testName = providedTestName,
        testMethod = testMethodIdentity(testMethodName, index = 1),
      )

      reportingData.humanReadableTestName shouldBe "MavenSmokeTests/addCustomRootsInMavenProject"
      reportingData.artifactPath shouldBe "maven-smoke-tests/1-add-custom-roots-in-maven-project"
    }
  }

  @Test
  fun `CurrentTestMethod based test name is not repeated`() {
    val displayName = "addCustomRootsInMavenProject"
    val methodName = "MavenSmokeTests/$displayName"
    val reportingData = reportingDataOf(
      testName = methodName.hyphenateTestName(),
      testMethod = testMethodIdentity(methodName, index = 1),
    )

    reportingData.artifactPath shouldBe "maven-smoke-tests/1-add-custom-roots-in-maven-project"
  }

  @Test
  fun `CurrentTestMethod based launch name is not repeated`() {
    val displayName = "addCustomRootsInMavenProject"
    val methodName = "MavenSmokeTests/$displayName"
    val reportingData = reportingDataOf(
      testName = "cached-project",
      testMethod = testMethodIdentity(methodName, index = 1),
      requestedLaunchName = displayName.hyphenateTestName(),
    )

    reportingData.artifactPath shouldBe "cached-project/maven-smoke-tests/1-add-custom-roots-in-maven-project"
    reportingData.humanReadableTestName shouldBe methodName
    reportingData.launchDirSegments().last() shouldBe "1_add-custom-roots-in-maven-project"
  }

  /**
   * A parameterized display name carries the slashes of its parameters, and the launch named after it flattens them into one directory, so
   * the two only match once both are spelled the way the reporting directories spell them.
   */
  @Test
  fun `a launch name matching a parameterized method name is not repeated`() {
    val reportingData = reportingDataOf(
      testName = "rust-rover-completion-test",
      testMethod = testMethodIdentity("RustRoverCompletionTest/completion/param-1", index = 1),
      requestedLaunchName = "completion/param-1",
    )

    reportingData.launchDirSegments() shouldBe listOf("1_completion-param-1")
    reportingData.humanReadableTestName shouldBe "RustRoverCompletionTest/completion/param-1"
  }

  /**
   * A launch name largely says what the method above it is called, so `completion/exceptions-prefix-hot-cache` below
   * `1_test-completion-exception-prefix-hot-cache` spent two directories on what the one above them said already. That cost a Windows agent
   * its profiler snapshot, which is what the length case further down measures.
   */
  @Test
  fun `only the last level of a launch name gets a directory, bounded shorter than the levels above`() {
    val reportingData = diasporaCompletionLaunch(cache = "Hot")

    reportingData.launchDirSegments() shouldBe listOf(
      "ruby-diaspora-performance-test",
      "1_test-completion-exception-prefix-hot-cache",
      "exceptions-prefix-ho-${ReportingPathUtils.nameHash("completion/exceptions-prefix-hot-cache")}",
    )
  }

  /** The launch name tells a human which scenario ran, and it has no path to fit in. Only the directories are cut down. */
  @Test
  fun `a launch keeps its whole name in the report a human reads`() {
    val reportingData = diasporaCompletionLaunch(cache = "Hot")

    reportingData.humanReadableTestName shouldBe
      "RubyDiasporaPerformanceTest/testCompletionExceptionPrefixHotCache()/completion/exceptions-prefix-hot-cache"
  }

  /** The levels above the last are gone, and the hash of the whole name is what keeps two launches of one method apart. */
  @Test
  fun `a launch name keeps its last level and a hash of the whole of it`() {
    val reportingData = reportingDataOf(
      testName = "some-project-test",
      testMethod = testMethodIdentity("TypingPerformanceTest/testInFile()", index = 1),
      requestedLaunchName = "typing/in-file",
    )

    reportingData.launchDirSegments() shouldBe
      listOf("typing-performance-test", "1_test-in-file", "in-file-${ReportingPathUtils.nameHash("typing/in-file")}")
  }

  /** Two launches of one method report side by side, so a name the method spells out completely still has to keep them apart. */
  @Test
  fun `launches of one method that the method spells out completely keep separate directories`() {
    fun launchOf(launchName: String): IDEReportingData = reportingDataOf(
      testName = "some-project-test",
      testMethod = testMethodIdentity("PerformanceTest/testFirstCodeAnalysisAndTyping()", index = 1),
      requestedLaunchName = launchName,
    )
    val firstCodeAnalysis = launchOf("firstCodeAnalysis")
    val typing = launchOf("typing")

    firstCodeAnalysis.launchDirSegments().drop(2) shouldBe listOf("firstCodeAnalysis")
    typing.launchDirSegments().drop(2) shouldBe listOf("typing")
    // what they publish and what a human reads must not become the same either
    firstCodeAnalysis.artifactPath shouldNotBe typing.artifactPath
    firstCodeAnalysis.humanReadableTestName shouldNotBe typing.humanReadableTestName
  }

  /** Cutting a launch level down must not cut away what tells one launch of a method from another. */
  @Test
  fun `launches of one method that differ past what the method spells out keep separate directories`() {
    fun launchNameOf(run: Int): String = "firstCodeAnalysis/exceptions-prefix/$run"
    fun launchDirsOf(run: Int): List<String> = reportingDataOf(
      testName = "diaspora-project-test",
      testMethod = testMethodIdentity("RubyDiasporaPerformanceTest/testFirstCodeAnalysisExceptionsPrefix()", index = 1),
      requestedLaunchName = launchNameOf(run),
    ).launchDirSegments().drop(2)

    // the method spells out both `firstCodeAnalysis` and `exceptions-prefix`, but not the run number that tells the two apart
    launchDirsOf(1) shouldBe listOf("1-${ReportingPathUtils.nameHash(launchNameOf(1))}")
    launchDirsOf(2) shouldBe listOf("2-${ReportingPathUtils.nameHash(launchNameOf(2))}")
  }

  /** Only the last level of a launch name gets a directory, so two launches differing only above it would otherwise land in one. */
  @Test
  fun `launches of one method that differ only above their last level keep separate directories`() {
    fun launchOf(launchName: String): IDEReportingData = reportingDataOf(
      testName = "some-project-test",
      testMethod = testMethodIdentity("PerformanceTest/testFirstCodeAnalysisAndTyping()", index = 1),
      requestedLaunchName = launchName,
    )
    val warmup = launchOf("warmup/typing")
    val startup = launchOf("startup/typing")

    // `typing` is all that is left of both, so the hash of the whole name is what keeps the two directories apart
    warmup.launchDirSegments().drop(2) shouldBe listOf("typing-${ReportingPathUtils.nameHash("warmup/typing")}")
    startup.launchDirSegments().drop(2) shouldBe listOf("typing-${ReportingPathUtils.nameHash("startup/typing")}")
    warmup.artifactPath shouldNotBe startup.artifactPath
  }

  /**
   * The class is named above every launch of every method in it, so a level it happens to spell out goes from all of them at once:
   * `CompletionPerformanceTest` says both `completion` and `performance`, which is everything that tells these two launches apart.
   */
  @Test
  fun `launches a class name cuts down to the same level keep separate directories`() {
    fun launchOf(launchName: String): IDEReportingData = reportingDataOf(
      testName = "some-project-test",
      testMethod = testMethodIdentity("CompletionPerformanceTest/testHotCache()", index = 1),
      requestedLaunchName = launchName,
    )
    val completion = launchOf("completion/1")
    val performance = launchOf("performance/1")

    completion.launchDirSegments().drop(2) shouldBe listOf("1-${ReportingPathUtils.nameHash("completion/1")}")
    performance.launchDirSegments().drop(2) shouldBe listOf("1-${ReportingPathUtils.nameHash("performance/1")}")
    completion.artifactPath shouldNotBe performance.artifactPath
  }

  @Test
  fun `a launch level the method only begins like is still spelled out`() {
    val reportingData = reportingDataOf(
      testName = "completion-cache-test",
      testMethod = testMethodIdentity("CompletionCacheTest/testCompletionCache()", index = 1),
      requestedLaunchName = "completions",
    )

    reportingData.launchDirSegments() shouldBe listOf("1_test-completion-cache", "completions")
  }

  @Test
  fun `a provided test name the method spells out is not repeated in the report or in the artifact path`() {
    val reportingData = reportingDataOf(
      testName = "test-metadata-scheme-generation-dev-server",
      testMethod = testMethodIdentity("MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer", index = 1),
    )

    reportingData.humanReadableTestName shouldBe
      "MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer"
    // the test name is the method's own name, which the directory of the method spells out, so the class names the test instead
    reportingData.artifactPath shouldBe
      "metadata-scheme-generation-dev-server-ag-f68c/1-test-metadata-scheme-generation-dev-server"
  }

  /** A project name is not a method name, however much of it the class repeats: the two projects have to stay two published paths. */
  @Test
  fun `two projects run through one method keep their own artifact paths`() {
    fun artifactPathOf(project: String): String = reportingDataOf(
      testName = project,
      testMethod = testMethodIdentity("TypingPerformanceTest/testInFile()", index = 1),
    ).artifactPath

    artifactPathOf("typing") shouldBe "typing/typing-performance-test/1-test-in-file"
    artifactPathOf("performance") shouldBe "performance/typing-performance-test/1-test-in-file"
  }

  /** Running into `class-method` is not being named after the method: such a test name still names a project of its own. */
  @Test
  fun `a test name that merely runs into the method name keeps its own artifact path`() {
    val reportingData = reportingDataOf(
      testName = "tests-add-custom-roots-in-maven-project",
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )

    reportingData.artifactPath shouldBe
      "tests-add-custom-roots-in-maven-project/maven-smoke-tests/1-add-custom-roots-in-maven-project"
  }

  @Test
  fun `a nested provided test name is compacted before the method artifact directories`() {
    val providedTestName = "completion/bat/src/bin/bat/clap-app.rs"
    val reportingData = reportingDataOf(
      testName = providedTestName,
      testMethod = testMethodIdentity("RustRoverCompletionTest/completion", index = 1),
    )

    reportingData.artifactPath shouldBe
      "${ReportingPathUtils.testDirectoryName(providedTestName)}/rust-rover-completion-test/1-completion"
  }

  @Test
  fun `a long launch name is cut the same way in the reporting directory and in the artifact path`() {
    val launchName = "q".repeat(256)
    val reportingData = reportingDataOf(testName = "qodana-test", requestedLaunchName = launchName)

    val launchDirName = reportingData.launchDirSegments().single()
    launchDirName.toByteArray(Charsets.UTF_8).size shouldBeLessThanOrEqual MAX_LAUNCH_DIR_NAME_LENGTH_IN_BYTES
    HASH_SUFFIX.containsMatchIn(launchDirName) shouldBe true
    reportingData.artifactPath shouldBe "qodana-test/$launchDirName"
    // only the name reported to a human keeps the launch name whole, having no path to fit in
    reportingData.humanReadableTestName shouldBe "qodana-test/$launchName"
  }

  /**
   * A cut can land on a separator, where the name itself has ended, and a directory named after nothing but the hash would be left below
   * it.
   */
  @Test
  fun `a name cut on a separator leaves no directory named after nothing`() {
    val firstLevelLength = MAX_DIR_NAME_LENGTH_IN_BYTES - NAME_HASH_LENGTH - 2
    val name = "q".repeat(firstLevelLength) + "/" + "w".repeat(20)

    ReportingPathUtils.dirName(name) shouldBe "q".repeat(firstLevelLength) + "-" + ReportingPathUtils.nameHash(name)
  }

  @Test
  fun `launch names that differ only past the truncation prefix use separate directories`() {
    val commonPrefix = "q".repeat(256)
    val first = launchDirNameOf("$commonPrefix-first")
    val second = launchDirNameOf("$commonPrefix-second")

    (first == second) shouldBe false
    launchDirNameOf("$commonPrefix-first") shouldBe first
  }

  /**
   * Whoever names the published artifacts of a launch has to spell the path the way the CI takes it, which is what `artifactPath` is for.
   */
  @Test
  fun `the artifact path hyphenates what a test name cannot be published with, keeping the segments apart`() {
    val reportingData = reportingDataOf(
      testName = "com.intellij.ide.SomeTest.opens a project(param 1)",
      requestedLaunchName = "first launch",
    )

    reportingData.humanReadableTestName shouldBe "com.intellij.ide.SomeTest.opens a project(param 1)/first launch"
    // the dots and the slash are what the path is built of, so they stay; the hyphen the closing bracket leaves behind is a wart of
    // `replaceSpecialCharactersWithHyphens` that the published artifacts have anyway
    reportingData.artifactPath shouldBe "com.intellij.ide.SomeTest.opens-a-projec-78a2/first-launch"
  }

  @Test
  fun `an IDE used by one test keeps the legacy artifact path expected by IJPerf`() {
    val displayName = "addCustomRootsInMavenProject"
    val reportingData = reportingDataOf(
      testName = "maven-smoke-tests",
      testMethod = testMethodIdentity("MavenSmokeTests/$displayName", index = 1),
      requestedLaunchName = displayName.hyphenateTestName(),
      isPartOfReusedIdeRun = false,
    )

    reportingData.artifactPath shouldBe "maven-smoke-tests/add-custom-roots-in-maven-project"
  }

  @Test
  fun `a standalone frontend keeps legacy path collisions away with an artifact name prefix`() {
    val reportingData = reportingDataOf(
      testName = "split-mode-test",
      isFrontend = true,
      isPartOfReusedIdeRun = false,
    )
    val testContext = mock(IDETestContext::class.java)

    reportingData.publishArtifact(testContext, reportingData.logsDir, "logs")

    val invocation = mockingDetails(testContext).invocations.single()
    invocation.arguments[0] shouldBe reportingData.logsDir
    invocation.arguments[1] shouldBe "split-mode-test"
    Regex("logs-frontend-[0-9]{14}").matches(invocation.arguments[2] as String) shouldBe true
  }

  // endregion

  // region Where a launch reports

  @Test
  fun `a launch creates its reporting dirs below the root, under the dirs its name asks for`() {
    val reportingData = reportingDataOf(
      testName = "maven-smoke-tests",
      testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
    )
    val launchDir = Path.of("1_add-custom-roots-in-maven-project")

    reportingRoot.relativize(reportingData.logsDir) shouldBe launchDir.resolve("log")
    reportingRoot.relativize(reportingData.reportsDir) shouldBe launchDir.resolve("reports")
    reportingRoot.relativize(reportingData.snapshotsDir) shouldBe launchDir.resolve("snapshots")
    reportingData.logsDir.exists() shouldBe true
    reportingData.jvmCrashLogDir shouldBe reportingData.logsDir.resolve("jvm-crash")
    reportingData.jbrDiagnosticDir shouldBe reportingData.logsDir.resolve("jbrDiagnostic")
    // unlike the three reporting dirs, the crash dirs are only created once the launch has a crash to write into them
    reportingData.jvmCrashLogDir.exists() shouldBe false
    reportingData.jbrDiagnosticDir.exists() shouldBe false
  }

  /** Both halves of a split-mode launch report below one root, so that their artifacts stay in one place. */
  @Test
  fun `a frontend takes a directory of its own inside the launch of the backend, locally and when publishing`() {
    val testMethod = testMethodIdentity("MavenSmokeTests/addCustomRootsInMavenProject", index = 1)
    val backend = reportingDataOf(testName = "maven-smoke-tests", testMethod = testMethod)
    val frontend = reportingDataOf(testName = "maven-smoke-tests", testMethod = testMethod, isFrontend = true)

    frontend.logsDir shouldBe backend.logsDir.parent.resolve("frontend").resolve("log")
    frontend.artifactPath shouldBe backend.artifactPath + "/frontend"
    // the directory is what tells the two IDEs of one launch apart, so what they publish no longer has to
    frontend.humanReadableTestName shouldBe backend.humanReadableTestName
  }

  /**
   * The snapshot of `testCompletionExceptionPrefixColdCache` came to 263 characters on a Windows agent, and async-profiler answered with
   * `Could not open Flight Recorder output file`. The two launch directories, `completion` and `exceptions-prefix-cold-cache`, said what
   * the method above them said already.
   */
  @Test
  fun `the snapshots of a deeply nested performance launch fit within the limit`() {
    // the longer of the two methods that failed: `cold` is a character more than `hot`
    val reportingData = diasporaCompletionLaunch(cache = "Cold")

    val snapshot = reportingRoot.relativize(reportingData.snapshotsDir).resolve(PROFILER_SNAPSHOT_NAME)
    CI_REPORTING_ROOT_LENGTH + 1 + snapshot.toString().length shouldBeLessThan ReportingPathUtils.PATH_LENGTH_LIMIT
  }

  /** The thread dumps go deeper than the reporting dirs, so the launch has to leave room for them as well as for its own logs. */
  @Test
  fun `the thread dumps of a launch named after its whole method fit within the limit`() {
    val threadDump = reportingRoot.relativize(gradleDelegateRunLaunch().logsDir).resolve(THREAD_DUMP_NAME)

    CI_GRADLE_REPORTING_ROOT_LENGTH + 1 + threadDump.toString().length shouldBeLessThan ReportingPathUtils.PATH_LENGTH_LIMIT
  }

  /**
   * The JVM expands `%p` only once it has already crashed, so the room a crash log needs is the room its widest name needs: a directory
   * that fits `-XX:ErrorFile` but not the file it names loses exactly the diagnostics the crash was supposed to leave behind.
   */
  @Test
  fun `the JVM crash log of a deeply nested performance launch fits within the limit`() {
    val reportingData = diasporaCompletionLaunch(cache = "Cold")

    val crashLog = reportingRoot.relativize(reportingData.jvmCrashLogDir).resolve(ReportingPathUtils.WIDEST_CRASH_LOG_NAME)
    CI_REPORTING_ROOT_LENGTH + 1 + crashLog.toString().length shouldBeLessThan ReportingPathUtils.PATH_LENGTH_LIMIT
  }

  /** A launch name is a path of its own, so the level of it that becomes a directory has to be kept from pointing above the launch. */
  @Test
  fun `a dot segment ending a launch name cannot escape the reporting root`() {
    val launchName = "../.."
    val reportingData = reportingDataOf(
      testName = "traversal-test",
      testMethod = testMethodIdentity("TraversalTest/traversal", index = 1),
      requestedLaunchName = launchName,
    )

    reportingData.logsDir.normalize().startsWith(reportingRoot.normalize()) shouldBe true
    reportingRoot.relativize(reportingData.logsDir) shouldBe
      Path.of("1_traversal", "%2E%2E-${ReportingPathUtils.nameHash(launchName)}", "log")
  }

  /** A launch name is a path of its own, and one that starts at the root would be resolved away from the reporting root altogether. */
  @Test
  fun `a launch name starting at the root reports below the reporting root all the same`() {
    val reportingData = reportingDataOf(
      testName = "traversal-test",
      testMethod = testMethodIdentity("TraversalTest/traversal", index = 1),
      requestedLaunchName = "/outside",
    )

    reportingData.logsDir.normalize().startsWith(reportingRoot.normalize()) shouldBe true
    reportingRoot.relativize(reportingData.logsDir) shouldBe
      Path.of("1_traversal", "outside-${ReportingPathUtils.nameHash("/outside")}", "log")
  }

  /** A launch name of nothing but separators asks for no directory, rather than for the root of the file system. */
  @Test
  fun `a launch name of separators alone asks for no directory of its own`() {
    val reportingData = reportingDataOf(
      testName = "traversal-test",
      testMethod = testMethodIdentity("TraversalTest/traversal", index = 1),
      requestedLaunchName = "//",
    )

    reportingRoot.relativize(reportingData.logsDir) shouldBe Path.of("1_traversal", "log")
  }

  /**
   * A launch name trailing off into a separator has named its last level one level up, so that is the level that gets the directory — not
   * nothing at all, which would drop the launch into the directory of its method, its logs mixed in with every other launch reporting
   * there.
   */
  @Test
  fun `a launch name ending in a separator still gets a directory of its own`() {
    fun launchOf(launchName: String): IDEReportingData = reportingDataOf(
      testName = "gradle-import-test",
      testMethod = testMethodIdentity("GradleImportTest/importsAProject", index = 1),
      requestedLaunchName = launchName,
    )
    val trailing = launchOf("gradle-import/")
    val plain = launchOf("gradle-import")

    trailing.launchDirSegments() shouldBe
      listOf("1_imports-a-project", "gradle-import-${ReportingPathUtils.nameHash("gradle-import/")}")
    // and not the directory of the launch actually named after that level, whose name it is only a part of
    plain.launchDirSegments() shouldBe listOf("1_imports-a-project", "gradle-import")
  }

  @Test
  fun `dot segments in parameterized display names cannot escape the reporting root`() {
    val reportingData = reportingDataOf(
      testName = "traversal-test",
      testMethod = testMethodIdentity("TraversalTest/../../../outside", index = 1),
    )

    reportingData.logsDir.normalize().startsWith(reportingRoot.normalize()) shouldBe true
    reportingRoot.relativize(reportingData.logsDir) shouldBe Path.of("1_..-..-..-outside", "log")
  }

  @Test
  fun `an overlong reporting directory is reported instead of replacing the method name with a hash`() {
    val reported = failuresReportedWhile {
      reportingDataOf(
        testName = "rust-rover-completion-test",
        testMethod = testMethodIdentity(
          "RustRoverCompletionTest/[6] FileParameters(fileToOpen=crates/bevy_pbr/src/render/light.rs, line=683, column=62)",
          index = 6,
        ),
        root = reportingRootWithAbsoluteLength(210),
      )
    }

    reported.first().message shouldContain "${ReportingPathUtils.PATH_LENGTH_LIMIT}-character limit"
  }

  @Test
  fun `a reporting directory is accepted when its complete path fits`() {
    val root = reportingRootWithAbsoluteLength(210)
    val reportingData = reportingDataOf(testName = "qodana-test", requestedLaunchName = "short-launch", root = root)

    reportingData.logsDir shouldBe root.resolve("short-launch/log")
  }

  @Test
  fun `an impossibly deep reporting root is reported by the path it made too long`() {
    val root = reportingRootWithAbsoluteLength(240)

    val reported = failuresReportedWhile {
      reportingDataOf(
        testName = "rust-rover-completion-test",
        testMethod = testMethodIdentity("RustRoverCompletionTest/completion", index = 1),
        root = root,
      )
    }

    reported.first().message shouldContain root.toString()
  }

  // endregion

  private fun reportingDataOf(
    testName: String,
    requestedLaunchName: String? = null,
    testMethod: TestMethodReportingIdentity? = null,
    isFrontend: Boolean = false,
    isPartOfReusedIdeRun: Boolean = true,
    root: Path = reportingRoot,
  ): IDEReportingData = IDEReportingData(
    reportingRoot = root,
    testName = testName,
    testMethod = testMethod,
    launchName = requestedLaunchName,
    isFrontend = isFrontend,
    artifactLayout = if (isPartOfReusedIdeRun) IDEReportingData.ArtifactLayout.REUSED_IDE else IDEReportingData.ArtifactLayout.LEGACY
  )

  /** The Gradle launch whose test name is the `CurrentTestMethod` form, so the test directory spells the class and the method out. */
  private fun gradleDelegateRunLaunch(): IDEReportingData {
    val methodName = "DelegateRunAGradleTaskToIdeaTest/delegateRunAGradleTaskToIdeaWsl"
    return reportingDataOf(testName = methodName.hyphenateTestName(), testMethod = testMethodIdentity(methodName, index = 1))
  }

  /** The RubyMine completion launch whose path went over Windows' limit, in its `Hot` and its `Cold` cache variant. */
  private fun diasporaCompletionLaunch(cache: String): IDEReportingData = reportingDataOf(
    testName = "diaspora-project-test",
    testMethod = testMethodIdentity("RubyDiasporaPerformanceTest/testCompletionExceptionPrefix${cache}Cache()", index = 1),
    requestedLaunchName = "completion/exceptions-prefix-${cache.lowercase()}-cache",
  )

  /** The directory names a launch reports under, read back off the tree it created below [reportingRoot]. */
  private fun IDEReportingData.launchDirSegments(): List<String> =
    reportingRoot.relativize(logsDir.parent).map(Path::toString)

  /** The directory segment a test method name gets, read back off the launch it belongs to. */
  private fun methodDirNameOf(testMethodName: String, index: Int = 1): String = reportingDataOf(
    testName = "reporting-data-test",
    testMethod = testMethodIdentity(testMethodName, index),
    requestedLaunchName = "launch",
  ).launchDirSegments().first()

  private fun launchDirNameOf(launchName: String): String = reportingDataOf(
    testName = "reporting-data-test",
    requestedLaunchName = launchName,
  ).launchDirSegments().single()

  private fun reportingRootWithAbsoluteLength(targetLength: Int): Path {
    val root = reportingRoot.toAbsolutePath().normalize()
    val paddingLength = targetLength - root.toString().length - 1
    require(paddingLength > 0)
    return root.resolve("x".repeat(paddingLength))
  }

  private fun testMethodIdentity(name: String, index: Int): TestMethodReportingIdentity = TestMethodReportingIdentity(
    className = name.substringBefore('/', missingDelimiterValue = ""),
    displayName = name.substringAfter('/', missingDelimiterValue = name),
    executionIndex = index,
  )

  private fun MutableMap<String, IDEReportingData>.register(testMethodName: String): IDEReportingData =
    getOrPut(testMethodName) {
      reportingDataOf(
        testName = "add-custom-src-root-in-maven-project-test",
        testMethod = testMethodIdentity(testMethodName, index = size + 1),
      )
    }
}
