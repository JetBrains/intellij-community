package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.report.DetailsOnCI
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
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
        TestMethodIdentity(
          className = clazzSimpleName,
          displayName = displayName,
          executionIndex = registered.count { registration -> registration.testMethodId != null } + 1,
        )
      }
      val reportingData = IDEReportingData(
        reportingRoot = testContext.paths.reportingRoot,
        testName = testContext.testName,
        testMethod = testMethod,
        requestedLaunchName = launchName,
        isFrontend = testContext.testCase.ideInfo.isFrontend,
      )
      reportArtifactsLink("Link to Logs and artifacts", reportingData)
      registered.firstOrNull()?.reportingData
        ?.takeUnless { it.artifactPath == reportingData.artifactPath }
        ?.let { reportArtifactsLink("Link to Logs and artifacts (IDE Startup)", it) }
      actionToResetLogDir.invoke(reportingData.logsDir)
      registered.add(Registration(testMethodId, reportingData))
      return reportingData
    }
  }

  /**
   * Publishes a link to the artifacts of a launch as soon as it is registered, so that a running test can already be followed on CI.
   * Lives here rather than in [IDEReportingData] so that asking a launch for its name has no effect.
   */
  private fun reportArtifactsLink(name: String, reportingData: IDEReportingData) {
    val link = DetailsOnCI.instance.getLinkToCIArtifacts(reportingData) ?: return
    TeamCityReporter.reportTestMetadata(
      testName = null,
      type = TeamCityReporter.MetadataType.LINK,
      flowId = null,
      name = name,
      value = link,
    )
  }
}
