package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.CurrentTestPlan
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.TestMethod
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.nio.file.Path

class IDERunContextTest {
  @TempDir
  lateinit var tempDir: Path

  @AfterEach
  fun clearCurrentTestMethod() {
    CurrentTestMethod.set(null)
  }

  @Test
  fun `repeating the current method is idempotent but switching back to an earlier method fails`() {
    CurrentTestMethod.set(testMethod("first test"))
    val testContext = testContext()
    doReturn("reused-ide-test").`when`(testContext).testName
    doReturn(IDEDataPaths(tempDir, null)).`when`(testContext).paths
    val runContext = IDERunContext(testContext)
    val firstTestReportingData = runContext.originalIdeReportingData
    val switchedLogDirs = mutableListOf<Path>()
    firstTestReportingData.artifactPath shouldBe "reused-ide-test"

    val firstTestReportingDataAgain = runContext.registerNewIdeReportingData(switchedLogDirs::add)
    CurrentTestMethod.set(testMethod("second test"))
    val secondTestReportingData = runContext.registerNewIdeReportingData(switchedLogDirs::add)
    CurrentTestMethod.set(testMethod("first test"))
    val error = assertThrows<IllegalStateException> {
      runContext.registerNewIdeReportingData(switchedLogDirs::add)
    }

    error.message shouldBe "Test method 'first test' was activated again after another test method"
    (firstTestReportingDataAgain === firstTestReportingData) shouldBe true
    firstTestReportingData.artifactPath shouldBe "reused-ide-test"
    secondTestReportingData.artifactPath shouldBe "reused-ide-test/ide-run-context-test/2-second-test"
    runContext.registeredIdeReportingData() shouldBe listOf(firstTestReportingData, secondTestReportingData)
    runContext.ideReportingDataFromCurrentToOldest() shouldBe listOf(secondTestReportingData, firstTestReportingData)
    (runContext.lastIdeReportingData === secondTestReportingData) shouldBe true
    switchedLogDirs shouldBe listOf(secondTestReportingData.logsDir)
  }

  @Test
  fun `a new test plan runs the same method again instead of failing`() {
    CurrentTestMethod.set(testMethod("first test"))
    val testContext = testContext()
    doReturn("reused-ide-test").`when`(testContext).testName
    doReturn(IDEDataPaths(tempDir, null)).`when`(testContext).paths
    val runContext = IDERunContext(testContext)
    val firstPlanReportingData = runContext.registerNewIdeReportingData {}
    CurrentTestMethod.set(testMethod("second test"))
    runContext.registerNewIdeReportingData {}

    // What the warm-IDE harnesses do: the plan ends, the IDE stays, and the next plan replays the same ids.
    CurrentTestPlan.beginNew()
    CurrentTestMethod.set(testMethod("first test"))
    val secondPlanReportingData = runContext.registerNewIdeReportingData {}

    (secondPlanReportingData === firstPlanReportingData) shouldBe false
    (secondPlanReportingData.logsDir == firstPlanReportingData.logsDir) shouldBe false
    secondPlanReportingData.artifactPath shouldBe "reused-ide-test/ide-run-context-test/3-first-test"
  }

  @Test
  fun `JUnit identities distinguish colliding readable test names`() {
    CurrentTestMethod.set(testMethod("same test", id = "first-id"))
    val testContext = testContext()
    doReturn("reused-ide-test").`when`(testContext).testName
    doReturn(IDEDataPaths(tempDir, null)).`when`(testContext).paths
    val runContext = IDERunContext(testContext)
    val firstTestReportingData = runContext.originalIdeReportingData

    CurrentTestMethod.set(testMethod("same test", id = "second-id"))
    val secondTestReportingData = runContext.registerNewIdeReportingData {}

    runContext.registeredIdeReportingData() shouldBe listOf(firstTestReportingData, secondTestReportingData)
    (firstTestReportingData.logsDir == secondTestReportingData.logsDir) shouldBe false
  }

  @Test
  fun `a launch no test method claims reports no link of its own`() {
    val testContext = testContext("shared-ide-test")

    val whileTheIdeStarts = testMetadataReportedWhile("shared-ide-test") { IDERunContext(testContext) }

    whileTheIdeStarts shouldBe emptyList()
  }

  @Test
  fun `every test method that claims a launch reports its links, the startup launch among them`() {
    val testContext = testContext("shared-ide-test")
    val runContext = IDERunContext(testContext)

    val forTheFirstTest = testMetadataReportedWhile("shared-ide-test") {
      CurrentTestMethod.set(testMethod("first test"))
      runContext.registerNewIdeReportingData {}
    }
    val forTheSecondTest = testMetadataReportedWhile("shared-ide-test") {
      CurrentTestMethod.set(testMethod("second test"))
      runContext.registerNewIdeReportingData {}
    }

    forTheFirstTest shouldBe listOf(
      ReportedMetadata("Link to Logs and artifacts", linkToArtifacts("shared-ide-test/ide-run-context-test/1-first-test")),
      ReportedMetadata("Link to Logs and artifacts (IDE Startup)", linkToArtifacts("shared-ide-test")),
    )
    forTheSecondTest shouldBe listOf(
      ReportedMetadata("Link to Logs and artifacts", linkToArtifacts("shared-ide-test/ide-run-context-test/2-second-test")),
      ReportedMetadata("Link to Logs and artifacts (IDE Startup)", linkToArtifacts("shared-ide-test")),
    )
  }

  @Test
  fun `a launch the test that started it claims reports one link, being the startup launch itself`() {
    CurrentTestMethod.set(testMethod("the only test"))

    val whileTheIdeStarts = testMetadataReportedWhile("in-test-launch-test") { IDERunContext(testContext("in-test-launch-test")) }

    whileTheIdeStarts shouldBe listOf(ReportedMetadata("Link to Logs and artifacts", linkToArtifacts("in-test-launch-test")))
  }

  private fun testMethod(displayName: String, id: String = displayName): TestMethod = TestMethod(
    name = displayName,
    displayName = displayName,
    testClass = IDERunContextTest::class.java,
    id = id,
  )

  private fun testContext(testName: String? = null): IDETestContext {
    val context = mock(IDETestContext::class.java)
    val testCase = mock(TestCase::class.java)
    val ideInfo = mock(IdeInfo::class.java)
    doReturn(testCase).`when`(context).testCase
    doReturn(ideInfo).`when`(testCase).ideInfo
    doReturn(false).`when`(ideInfo).isFrontend
    testName?.let {
      doReturn(it).`when`(context).testName
      doReturn(IDEDataPaths(tempDir, null)).`when`(context).paths
    }
    return context
  }
}
