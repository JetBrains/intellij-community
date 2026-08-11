package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.FrontendIDEDataPaths
import java.nio.file.Path

internal class IDEReportingDataRegistry(
  private val testContext: IDETestContext,
  private val launchName: String,
) {
  private data class Registration(
    val testMethodId: String?,
    val reportingData: IDEReportingData,
  )

  private val lock = Any()
  private val registered = ArrayList<Registration>()

  val original: IDEReportingData = register {}
  val current: IDEReportingData get() = synchronized(lock) { registered.last().reportingData }

  fun all(): List<IDEReportingData> = synchronized(lock) {
    registered.map { it.reportingData }
  }

  fun fromCurrentToOldest(): List<IDEReportingData> = synchronized(lock) {
    registered.asReversed().map { it.reportingData }
  }

  /**
   * Returns the reporting data of the current test method. Re-registering the current method is idempotent, while returning to an
   * earlier method is an invalid lifecycle transition.
   */
  fun register(actionToResetLogDir: (Path) -> Unit): IDEReportingData {
    val currentTestMethod = CurrentTestMethod.get()
    val testMethodId = currentTestMethod?.id

    synchronized(lock) {
      registered.lastOrNull()?.takeIf { it.testMethodId == testMethodId }?.let { return it.reportingData }

      registered.firstOrNull { it.testMethodId == testMethodId }?.let {
        error("Test method '$testMethodId' was activated again after another test method")
      }

      val testMethod = currentTestMethod?.run {
        IDEReportingData.TestMethodData(
          className = clazzSimpleName,
          displayName = displayName,
          index = registered.count { registration -> registration.testMethodId != null } + 1,
        )
      }
      val reportingData = IDEReportingData(
        providedTestName = testContext.testName,
        launchName = launchName,
        testMethod = testMethod,
        testHome = testContext.paths.testHome,
        isFrontend = testContext.paths is FrontendIDEDataPaths,
      )
      registered.firstOrNull()?.reportingData?.let { reportingData.reportStartupArtifactsLink(it) }
      actionToResetLogDir.invoke(reportingData.logsDir)
      registered.add(Registration(testMethodId, reportingData))
      return reportingData
    }
  }
}
