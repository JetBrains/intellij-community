package com.intellij.ide.starter

import com.intellij.ide.starter.runner.IDEReportingData
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

private const val LONG_NAME_PREFIX = "reused-ide-process-reports-into-a-dir-of-its-own-per-test-method"
private const val MAX_DIR_NAME_LENGTH_IN_BYTES = 60
private val HASH_SUFFIX = Regex("-[0-9a-f]{12}$")

private fun longMethodName(distinguishedBy: String): String = "$LONG_NAME_PREFIX-$distinguishedBy"

/** A reused IDE process reports every test method into a directory of its own, named after the method and cut to keep the path short. */
class IDEReportingDataTest {
  @TempDir
  lateinit var testHome: Path

  @Test
  fun `a method name short enough to fit is prefixed with its execution index`() {
    dirNameOf("opens-a-project") shouldBe "1_opens-a-project"
  }

  @Test
  fun `long method names keep their index and get a stable distinguishing hash`() {
    val firstMethodName = longMethodName("first")
    val first = dirNameOf(firstMethodName, index = 1)
    val second = dirNameOf(longMethodName("second"), index = 2)

    first.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
    first.startsWith("1_") shouldBe true
    HASH_SUFFIX.containsMatchIn(first) shouldBe true
    dirNameOf(firstMethodName, index = 1) shouldBe first
    (first.removePrefix("1_") == second.removePrefix("2_")) shouldBe false
  }

  @Test
  fun `slashes inside long parameterized display names do not leave oversized parent dirs`() {
    val longParameterDescription = "test-case-ide-info-".repeat(20)
    val reportingData = IDEReportingData(
      providedTestName = "qodana-test",
      testMethod = testMethodData("QodanaTest/$longParameterDescription/lambda-id", index = 1),
      testHome = testHome,
    )
    val relativeLogsDir = testHome.relativize(reportingData.logsDir)
    val parameterDirName = relativeLogsDir.getName(1).toString()

    parameterDirName.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
    HASH_SUFFIX.containsMatchIn(parameterDirName) shouldBe true
    relativeLogsDir.getName(2).toString() shouldBe "1_lambda-id"
  }

  @Test
  fun `dot segments in parameterized display names cannot escape the reporting root`() {
    val reportingData = IDEReportingData(
      providedTestName = "traversal-test",
      testMethod = testMethodData("TraversalTest/../../../outside", index = 1),
      testHome = testHome,
    )

    reportingData.logsDir.normalize().startsWith(testHome.normalize()) shouldBe true
    testHome.relativize(reportingData.logsDir) shouldBe
      Path.of("traversal-test", "%2E%2E", "%2E%2E", "%2E%2E", "1_outside", "log")
  }

  /** A reused IDE process registers the reporting data of a method again on every switch back to it. */
  @Test
  fun `the same method name always gets the same dir back`() {
    val methodName = longMethodName("switched-away-from-and-back-to")

    val firstRegistration = dirNameOf(methodName, index = 1)
    dirNameOf(longMethodName("running-in-between"), index = 2)

    dirNameOf(methodName, index = 1) shouldBe firstRegistration
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
  fun `the execution index prefixes the method below its test class directory`() {
    val reportingData = IDEReportingData(
      providedTestName = "maven-smoke-tests",
      testMethod = testMethodData("MavenSmokeTests/addCustomRootsInMavenProject", index = 1),
      testHome = testHome,
    )
    val relativeLogsDir = testHome.relativize(reportingData.logsDir)

    relativeLogsDir.getName(0).toString() shouldBe "maven-smoke-tests"
    relativeLogsDir.getName(1).toString() shouldBe "1_add-custom-roots-in-maven-project"
    reportingData.artifactPath shouldBe "maven-smoke-tests/maven-smoke-tests/1-add-custom-roots-in-maven-project"
  }

  @Test
  fun `a provided test name containing the method gets indexed in place whether hyphenated or not`() {
    val testMethodName = "MavenSmokeTests/addCustomRootsInMavenProject"
    val providedTestNames = listOf(
      "maven-smoke-tests/add-custom-roots-in-maven-project",
      testMethodName,
    )

    providedTestNames.forEach { providedTestName ->
      val reportingData = IDEReportingData(
        providedTestName = providedTestName,
        testMethod = testMethodData(testMethodName, index = 1),
        testHome = testHome,
      )

      reportingData.humanReadableTestName shouldBe "MavenSmokeTests/addCustomRootsInMavenProject"
      reportingData.artifactPath shouldBe "maven-smoke-tests/1-add-custom-roots-in-maven-project"
    }
  }

  @Test
  fun `a provided test name matching the method name is not repeated in the reported test name`() {
    val providedTestName = "test-metadata-scheme-generation-dev-server"
    val testClassName = "metadata-scheme-generation-dev-server-aggregator-test"
    val reportingData = IDEReportingData(
      providedTestName = providedTestName,
      testMethod = testMethodData("MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer", index = 1),
      testHome = testHome,
    )

    reportingData.humanReadableTestName shouldBe "MetadataSchemeGenerationDevServerAggregatorTest/testMetadataSchemeGenerationDevServer"
    reportingData.artifactPath shouldBe "$providedTestName/$testClassName/1-test-metadata-scheme-generation-dev-server"
  }

  @Test
  fun `a long launch name is cut only in the reporting directory`() {
    val launchName = "q".repeat(256)
    val reportingData = IDEReportingData(
      providedTestName = "qodana-test",
      launchName = launchName,
      testHome = testHome,
    )

    val launchDirName = testHome.relativize(reportingData.logsDir).getName(0).toString()
    launchDirName.toByteArray(Charsets.UTF_8).size shouldBe MAX_DIR_NAME_LENGTH_IN_BYTES
    HASH_SUFFIX.containsMatchIn(launchDirName) shouldBe true
    reportingData.humanReadableTestName shouldBe "qodana-test/$launchName"
  }

  @Test
  fun `launch names that differ only past the truncation prefix use separate directories`() {
    val commonPrefix = "q".repeat(256)
    val first = reportingDataWithLaunchName("$commonPrefix-first")
    val second = reportingDataWithLaunchName("$commonPrefix-second")

    (first.logsDir.parent == second.logsDir.parent) shouldBe false
    reportingDataWithLaunchName("$commonPrefix-first").logsDir shouldBe first.logsDir
  }

  /** Whoever names the published artifacts of a launch has to spell the path the way the CI takes it, which is what `artifactPath` is for. */
  @Test
  fun `the artifact path hyphenates what a test name cannot be published with, keeping the segments apart`() {
    val reportingData = IDEReportingData(
      providedTestName = "com.intellij.ide.SomeTest.opens a project(param 1)",
      launchName = "first launch",
      testHome = testHome,
    )

    reportingData.humanReadableTestName shouldBe "com.intellij.ide.SomeTest.opens a project(param 1)/first launch"
    // the dots and the slash are what the path is built of, so they stay; the hyphen the closing bracket leaves behind is a wart of
    // `replaceSpecialCharactersWithHyphens` that the published artifacts have anyway
    reportingData.artifactPath shouldBe "com.intellij.ide.SomeTest.opens-a-project-param-1-/first-launch"
  }

  /** The directory segment [IDEReportingData] derives from a test method name, read back off one of its reporting dirs. */
  private fun dirNameOf(testMethodName: String, index: Int = 1, home: Path = testHome): String {
    val logsDir = IDEReportingData(
      providedTestName = "ide-reporting-data-test",
      launchName = "launch",
      testMethod = testMethodData(testMethodName, index),
      testHome = home,
    ).logsDir
    return home.relativize(logsDir).getName(0).toString()
  }

  private fun reportingDataWithLaunchName(launchName: String): IDEReportingData = IDEReportingData(
    providedTestName = "ide-reporting-data-test",
    launchName = launchName,
    testHome = testHome,
  )

  private fun testMethodData(name: String, index: Int): IDEReportingData.TestMethodData =
    IDEReportingData.TestMethodData(
      className = name.substringBefore('/', missingDelimiterValue = ""),
      displayName = name.substringAfter('/', missingDelimiterValue = name),
      index = index,
    )

  private fun MutableMap<String, IDEReportingData>.register(testMethodName: String): IDEReportingData =
    getOrPut(testMethodName) {
      IDEReportingData(
        providedTestName = "add-custom-src-root-in-maven-project-test",
        testMethod = testMethodData(testMethodName, index = size + 1),
        testHome = testHome,
      )
    }
}
