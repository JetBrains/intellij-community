package com.intellij.ide.starter

import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.ide.starter.runner.TestMethodIdentity
import com.intellij.ide.starter.utils.ReportingPathUtils
import com.intellij.ide.starter.utils.hyphenateTestName
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

private const val LONG_NAME_PREFIX = "reused-ide-process-reports-into-a-dir-of-its-own-per-test-method"
private const val MAX_DIR_NAME_LENGTH_IN_BYTES = 50
private val HASH_SUFFIX = Regex("-[0-9a-f]{6}$")

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

    first.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
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
    parameterDirName.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
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

  @Test
  fun `a provided test name matching the method name is not repeated in the reported test name`() {
    val providedTestName = "test-metadata-scheme-generation-dev-server"
    val reportingData = reportingDataOf(
      testName = providedTestName,
      testMethod = testMethodIdentity("MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer", index = 1),
    )

    reportingData.humanReadableTestName shouldBe
      "MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer"
    reportingData.artifactPath shouldBe
      "$providedTestName/metadata-scheme-generation-dev-server-aggre-f68c38/1-test-metadata-scheme-generation-dev-server"
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
    launchDirName.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
    HASH_SUFFIX.containsMatchIn(launchDirName) shouldBe true
    reportingData.artifactPath shouldBe "qodana-test/$launchDirName"
    // only the name reported to a human keeps the launch name whole, having no path to fit in
    reportingData.humanReadableTestName shouldBe "qodana-test/$launchName"
  }

  @Test
  fun `launch names that differ only past the truncation prefix use separate directories`() {
    val commonPrefix = "q".repeat(256)
    val first = launchDirNameOf("$commonPrefix-first")
    val second = launchDirNameOf("$commonPrefix-second")

    (first == second) shouldBe false
    launchDirNameOf("$commonPrefix-first") shouldBe first
  }

  /** Whoever names the published artifacts of a launch has to spell the path the way the CI takes it, which is what `artifactPath` is for. */
  @Test
  fun `the artifact path hyphenates what a test name cannot be published with, keeping the segments apart`() {
    val reportingData = reportingDataOf(
      testName = "com.intellij.ide.SomeTest.opens a project(param 1)",
      requestedLaunchName = "first launch",
    )

    reportingData.humanReadableTestName shouldBe "com.intellij.ide.SomeTest.opens a project(param 1)/first launch"
    // the dots and the slash are what the path is built of, so they stay; the hyphen the closing bracket leaves behind is a wart of
    // `replaceSpecialCharactersWithHyphens` that the published artifacts have anyway
    reportingData.artifactPath shouldBe "com.intellij.ide.SomeTest.opens-a-project-param-1-/first-launch"
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
    testMethod: TestMethodIdentity? = null,
    isFrontend: Boolean = false,
    root: Path = reportingRoot,
  ): IDEReportingData = IDEReportingData(
    reportingRoot = root,
    testName = testName,
    testMethod = testMethod,
    requestedLaunchName = requestedLaunchName,
    isFrontend = isFrontend,
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

  private fun testMethodIdentity(name: String, index: Int): TestMethodIdentity = TestMethodIdentity(
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
