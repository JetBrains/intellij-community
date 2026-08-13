package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.CurrentTestMethod
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

    val firstTestReportingDataAgain = runContext.registerNewIdeReportingData(switchedLogDirs::add)
    CurrentTestMethod.set(testMethod("second test"))
    val secondTestReportingData = runContext.registerNewIdeReportingData(switchedLogDirs::add)
    CurrentTestMethod.set(testMethod("first test"))
    val error = assertThrows<IllegalStateException> {
      runContext.registerNewIdeReportingData(switchedLogDirs::add)
    }

    error.message shouldBe "Test method 'first test' was activated again after another test method"
    (firstTestReportingDataAgain === firstTestReportingData) shouldBe true
    runContext.registeredIdeReportingData() shouldBe listOf(firstTestReportingData, secondTestReportingData)
    runContext.ideReportingDataFromCurrentToOldest() shouldBe listOf(secondTestReportingData, firstTestReportingData)
    (runContext.lastIdeReportingData === secondTestReportingData) shouldBe true
    switchedLogDirs shouldBe listOf(secondTestReportingData.logsDir)
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

  private fun testMethod(displayName: String, id: String = displayName): TestMethod = TestMethod(
    name = displayName,
    displayName = displayName,
    testClass = IDERunContextTest::class.java,
    id = id,
  )

  private fun testContext(): IDETestContext {
    val context = mock(IDETestContext::class.java)
    val testCase = mock(TestCase::class.java)
    val ideInfo = mock(IdeInfo::class.java)
    doReturn(testCase).`when`(context).testCase
    doReturn(ideInfo).`when`(testCase).ideInfo
    doReturn(false).`when`(ideInfo).isFrontend
    return context
  }
}
