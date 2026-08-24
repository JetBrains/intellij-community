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
    /** The [CurrentTestPlan] generation this method was activated in, so "again" can mean "in this plan". */
    val planGeneration: Int,
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
   * earlier method of the same test plan is an invalid lifecycle transition.
   *
   * Scoped to the plan rather than to this registry, because an IDE may outlive a test plan on purpose: a harness that keeps one warm
   * IDE and runs plan after plan against it replays the same method ids, and that is a second run of the method rather than a return to
   * it. Such a run registers reporting of its own — its own log directory, at the next execution index — so the two runs of one method
   * do not write into each other's logs, which is what the transition guard exists to prevent in the first place.
   */
  fun register(actionToResetLogDir: (Path) -> Unit): IDEReportingData {
    synchronized(lock) {
      val currentTestMethod = CurrentTestMethod.get()
      val testMethodId = currentTestMethod?.id
      val planGeneration = CurrentTestPlan.generation

      registered.lastOrNull()
        ?.takeIf { it.testMethodId == testMethodId && it.planGeneration == planGeneration }
        ?.let { return it.reportingData }

      registered.firstOrNull { it.testMethodId == testMethodId && it.planGeneration == planGeneration }?.let {
        error("Test method '$testMethodId' was activated again after another test method")
      }

      val reportingIdentity = currentTestMethod?.run {
        TestMethodReportingIdentity(
          className = clazzSimpleName,
          displayName = displayName,
          executionIndex = registered.count { registration -> registration.testMethodId != null } + 1,
        )
      }
      val reportingData = IDEReportingData(
        reportingRoot = testContext.paths.reportingRoot,
        testName = testContext.testName,
        testMethod = reportingIdentity,
        launchName = launchName,
        isFrontend = testContext.testCase.ideInfo.isFrontend,
        artifactLayout = if (registered.isNotEmpty()) IDEReportingData.ArtifactLayout.REUSED_IDE else IDEReportingData.ArtifactLayout.LEGACY
      )
      // only for a launch some test method claims, see CurrentTestMethod.get: an IDE started outside a test - from
      // `@BeforeAll` - would report its links onto the test that ran before it
      if (currentTestMethod != null) {
        reportArtifactsLink("Link to Logs and artifacts", reportingData)
        registered.firstOrNull()?.reportingData
          ?.takeUnless { it.artifactPath == reportingData.artifactPath }
          ?.let { reportArtifactsLink("Link to Logs and artifacts (IDE Startup)", it) }
      }
      actionToResetLogDir.invoke(reportingData.logsDir)
      registered.add(Registration(testMethodId, planGeneration, reportingData))
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
